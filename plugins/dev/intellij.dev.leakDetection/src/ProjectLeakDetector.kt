// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dev.leakDetection

import com.intellij.ide.IdeEventQueue
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.project.ex.ProjectEx
import com.intellij.openapi.project.impl.ProjectImpl
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.PairProcessor
import com.intellij.util.ReflectionUtil
import com.intellij.util.ref.DebugReflectionUtil
import com.jetbrains.JBR
import kotlinx.coroutines.delay
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import java.awt.KeyboardFocusManager
import java.util.IdentityHashMap
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds

private val LOG = logger<ProjectLeakDetector>()

// Mirrors the traversal bounds used by the test-framework LeakHunter.
private const val MAX_DEPTH = 1_000
private const val MAX_QUEUE_SIZE = 1_000_000

// When the first pass finds something, wait this long and GC again before re-collecting, to drop transiently-retained objects.
private const val SETTLE_DELAY_MS = 1_000L

private const val EDITOR_STALE_THRESHOLD_KEY = "dev.leak.detection.editor.staleThresholdMs"
private const val DEFAULT_EDITOR_STALE_THRESHOLD_MS = 10_000

@ApiStatus.Internal
enum class LeakKind { PROJECT, EDITOR }

@ApiStatus.Internal
data class LeakInfo(
  val kind: LeakKind,
  val className: String,
  val identityHashCode: Int,
  val description: String,
  /** Creation trace if the platform recorded one (usually only populated in unit-test mode). */
  val creationTrace: String?,
  /** For editors: how long the editor has been retained after disposal. */
  val staleForMs: Long?,
  /** A strong-reference path from a GC root to the leaked instance. */
  val referencePath: String,
)

/**
 * Runtime detector for leaked (disposed-but-still-referenced) [com.intellij.openapi.project.Project] and
 * [com.intellij.openapi.editor.Editor] instances. A custom, production-callable variant of the test-only
 * `com.intellij.testFramework.LeakHunter`, built on the public [DebugReflectionUtil.walkObjects].
 *
 * ### Coverage limitations
 * Detection walks the strong-reference graph from a best-effort set of GC roots (see [buildRoots]), so it cannot
 * find leaks anchored only by references the JVM does not expose to reflection:
 * - **Thread stack-frame locals** — a project pinned solely by a local variable in a running background task or
 *   coroutine frame is invisible. Only thread objects and their thread-locals are reachable, not stack slots.
 * - **Native / JNI global references** — anything pinned from native code (AWT peers, native handles, etc.).
 *
 * [detect] is heavy (forces a full GC and walks the object graph) and must be called off the EDT.
 */
@ApiStatus.Internal
class ProjectLeakDetector(
  /** Editors retained at least this long after disposal are reported. Defaults to the registry-configured value. */
  private val editorStaleThresholdMs: Long =
    Registry.intValue(EDITOR_STALE_THRESHOLD_KEY, DEFAULT_EDITOR_STALE_THRESHOLD_MS).toLong(),
) {
  suspend fun detect(): List<LeakInfo> {
    hardGc()
    // Give finalizers / soft references a chance to clear before the first scan.
    delay(200.milliseconds)
    hardGc()

    if (!LeakCandidateRegistry.getInstance().hasRetainedDisposedInstances()) {
      LOG.debug("No disposed Project/Editor retained after GC; skipping the leak object-graph walk")
      return emptyList()
    }

    var leaks = collect()
    if (leaks.isNotEmpty()) {
      // Re-scan after another GC: anything still reachable now is very unlikely to be a transient reference.
      delay(SETTLE_DELAY_MS.milliseconds)
      hardGc()
      leaks = collect()
    }
    return leaks
  }

  private suspend fun collect(): List<LeakInfo> {
    return readAction {
      val result = ArrayList<LeakInfo>()
      val roots = buildRoots()

      DebugReflectionUtil.walkObjects(MAX_DEPTH, MAX_QUEUE_SIZE, roots, ProjectImpl::class.java, { true },
                                      PairProcessor { project, backLink ->
                                        if (project.isDisposed && !project.isDefault && !project.isLight) {
                                          result.add(projectLeak(project, backLink))
                                        }
                                        true
                                      })

      DebugReflectionUtil.walkObjects(MAX_DEPTH, MAX_QUEUE_SIZE, roots, EditorImpl::class.java, { true },
                                      PairProcessor { editor, backLink ->
                                        val disposedAtNanos = editor.disposalTimestampNanos
                                        if (disposedAtNanos != 0L) {
                                          val staleMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - disposedAtNanos)
                                          if (staleMs >= editorStaleThresholdMs) {
                                            result.add(editorLeak(editor, staleMs, backLink))
                                          }
                                        }
                                        true
                                      })
      result
    }
  }

  // Disposer.getTree() is @TestOnly, but it is the canonical retention root and this is an internal dev-only diagnostic.
  @Suppress("TestOnlyProblems")
  @VisibleForTesting
  fun buildRoots(): Map<Any, String> {
    val roots = IdentityHashMap<Any, String>()
    ApplicationManager.getApplication()?.let { roots[it] = "ApplicationManager.getApplication()" }
    roots[Disposer.getTree()] = "Disposer.getTree()"
    roots[IdeEventQueue.getInstance()] = "IdeEventQueue.getInstance()"
    roots[Thread.getAllStackTraces().keys] = "all live threads"
    addAwtRoots(roots)
    addLoadedClassesStaticsRoots(roots)
    return roots
  }

  /**
   * AWT focus state lives in the [KeyboardFocusManager] and window statics in `sun.awt.AppContext`, neither
   * reachable through [IdeEventQueue].
   *
   * `Window.getWindows()` / `Frame.getFrames()` are intentionally not used: they materialize a strong `Window[]`
   * out of AWT's `Vector<WeakReference<Window>>` lists, which would strongify weak refs and report misleading/false
   * leaks (IJPL-249998). Strongly-held windows are reached via the statics/app roots instead.
   */
  private fun addAwtRoots(roots: MutableMap<Any, String>) {
    try {
      KeyboardFocusManager.getCurrentKeyboardFocusManager()?.let {
        roots[it] = "KeyboardFocusManager.getCurrentKeyboardFocusManager()"
      }
    }
    catch (t: Throwable) {
      LOG.warn("Cannot access the AWT KeyboardFocusManager root; leak detection will skip it", t)
    }

    // sun.awt.AppContext holds additional AWT statics; reach it best-effort (may be blocked by JPMS access rules).
    try {
      val appContext = Class.forName("sun.awt.AppContext").getMethod("getAppContext").invoke(null)
      if (appContext != null) {
        roots[appContext] = "sun.awt.AppContext.getAppContext()"
      }
    }
    catch (t: Throwable) {
      LOG.debug("sun.awt.AppContext root unavailable; skipping", t)
    }
  }

  /**
   * The classloaders whose static fields we try to cover: this plugin, the platform core, the system loader, every
   * loaded plugin, and every thread's context loader.
   */
  @VisibleForTesting
  fun staticsRootClassLoaders(): Set<ClassLoader> {
    val classLoaders = LinkedHashSet<ClassLoader>()

    ProjectLeakDetector::class.java.classLoader?.let { classLoaders.add(it) }
    ApplicationManager::class.java.classLoader?.let { classLoaders.add(it) }

    try {
      ClassLoader.getSystemClassLoader()?.let { classLoaders.add(it) }
    }
    catch (t: Throwable) {
      LOG.debug("Cannot access the system classloader", t)
    }

    try {
      for (plugin in PluginManagerCore.loadedPlugins) {
        plugin.pluginClassLoader?.let { classLoaders.add(it) }
      }
    }
    catch (t: Throwable) {
      LOG.debug("Cannot enumerate plugin classloaders", t)
    }

    for (thread in Thread.getAllStackTraces().keys) {
      try {
        thread.contextClassLoader?.let { classLoaders.add(it) }
      }
      catch (_: SecurityException) {
      }
    }

    return classLoaders
  }

  /**
   * Best-effort statics roots: the `classes` collection of each [staticsRootClassLoaders] entry, which pins every
   * class that loader defined and thus reaches their static fields.
   */
  private fun addLoadedClassesStaticsRoots(roots: MutableMap<Any, String>) {
    for (classLoader in staticsRootClassLoaders()) {
      try {
        val classes = ReflectionUtil.getField<Any>(classLoader.javaClass, classLoader, null, "classes") ?: continue
        roots[classes] = "statics of classes loaded by $classLoader"
      }
      catch (t: Throwable) {
        LOG.debug("Cannot access loaded-classes statics for ${classLoader.javaClass.name}", t)
      }
    }
  }

  // ProjectEx.getCreationTrace() is @TestOnly; it is best-effort here (usually empty outside unit tests) and only enriches the report.
  @Suppress("TestOnlyProblems")
  private fun projectLeak(project: ProjectImpl, backLink: DebugReflectionUtil.BackLink<*>): LeakInfo =
    LeakInfo(
      kind = LeakKind.PROJECT,
      className = project.javaClass.name,
      identityHashCode = System.identityHashCode(project),
      description = safeToString(project),
      creationTrace = (project as ProjectEx).creationTrace,
      staleForMs = null,
      referencePath = backLink.toString(),
    )

  private fun editorLeak(editor: EditorImpl, staleForMs: Long, backLink: DebugReflectionUtil.BackLink<*>): LeakInfo =
    LeakInfo(
      kind = LeakKind.EDITOR,
      className = editor.javaClass.name,
      identityHashCode = System.identityHashCode(editor),
      description = safeToString(editor),
      creationTrace = null,
      staleForMs = staleForMs,
      referencePath = backLink.toString(),
    )

  private fun safeToString(o: Any): String =
    try {
      o.toString()
    }
    catch (t: Throwable) {
      "(${t.javaClass.simpleName} while computing toString())"
    }

  private fun hardGc() {
    if (JBR.isSystemUtilsSupported()) JBR.getSystemUtils().fullGC() else System.gc()
  }
}
