// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.recentFiles.backend

import com.intellij.ide.plugins.cl.PluginAwareClassLoader
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.recentFiles.shared.FileChangeKind
import com.intellij.platform.recentFiles.shared.LocalRecentFilesEvent
import com.intellij.platform.recentFiles.shared.RecentFileEventsModel
import com.intellij.platform.recentFiles.shared.RecentFileKind
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.util.ref.GCUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.lang.ref.WeakReference
import java.nio.file.Path
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The replayed events of [RecentFileEventsModel] must keep no file of an unloaded plugin.
 */
@TestApplication
class RecentFileEventsModelPluginUnloadTest {
  private val projectFixture = projectFixture()
  private val project by projectFixture

  @Test
  fun `a removal of a file of an unloading plugin does not stay in the replayed events`() = runBlocking {
    val classLoaderRef = emitRemovalOfPluginFile(project, PluginAwareClassLoader.UNLOAD_IN_PROGRESS) {}

    assertCollected(classLoaderRef)
  }

  @Test
  fun `the reset after a plugin unload drops the replayed events`() = runBlocking {
    val classLoaderRef = emitRemovalOfPluginFile(project, PluginAwareClassLoader.ACTIVE) {
      // RecentFilesPluginUnloadListener does this for each open project after a plugin unload
      RecentFileEventsModel.getInstance(project).resetReplayedEvents()
    }

    assertCollected(classLoaderRef)
  }
}

/**
 * Emits the removal of a file of a test plugin, waits until the last flow gets the event, and then runs [afterRemoval].
 * The class loader of the plugin stays only in the returned reference, so that the caller can check its collection.
 */
private suspend fun emitRemovalOfPluginFile(
  project: Project,
  classLoaderState: Int,
  afterRemoval: () -> Unit,
): WeakReference<ClassLoader> {
  val classLoader = TestPluginAwareClassLoader(classLoaderState)
  val file = classLoader.loadPluginFileClass().getDeclaredConstructor().newInstance() as VirtualFile
  val model = RecentFileEventsModel.getInstance(project)
  withTimeout(10.seconds) {
    val lastFlowGotEvent = launch(start = CoroutineStart.UNDISPATCHED) {
      model.getRecentFiles(RecentFileKind.entries.last()).first { event ->
        event is LocalRecentFilesEvent.ItemsRemoved && event.batch.contains(file)
      }
    }
    model.scheduleApplyChanges(FileChangeKind.REMOVED, listOf(file))
    lastFlowGotEvent.join()
  }
  afterRemoval()
  return WeakReference(classLoader)
}

/**
 * Another collector of the model, for example the synchronizer of the recent files list, can get the event later.
 * So the check collects the garbage until the class loader is gone or the timeout ends.
 */
private suspend fun assertCollected(classLoaderRef: WeakReference<ClassLoader>) {
  val deadline = System.nanoTime() + 10.seconds.inWholeNanoseconds
  while (classLoaderRef.get() != null && System.nanoTime() < deadline) {
    GCUtil.tryGcSoftlyReachableObjects { classLoaderRef.get() == null }
    delay(10.milliseconds)
  }
  assertNull(classLoaderRef.get(), "The replayed events keep a file of an unloaded plugin")
}

internal class PluginVirtualFile : LightVirtualFile("plugin.txt", "content")

private class TestPluginAwareClassLoader(private val classLoaderState: Int) :
  ClassLoader(RecentFileEventsModelPluginUnloadTest::class.java.classLoader), PluginAwareClassLoader {

  private val pluginId = PluginId.getId("test.recent.files.plugin")
  private val descriptor = DefaultPluginDescriptor(pluginId, this)

  @Suppress("RAW_SCOPE_CREATION")
  private val coroutineScope = CoroutineScope(Job())

  fun loadPluginFileClass(): Class<*> {
    return loadClassInsideSelf(PluginVirtualFile::class.java.name) ?: error("Cannot load ${PluginVirtualFile::class.java.name}")
  }

  override fun getPluginDescriptor(): PluginDescriptor = descriptor
  override fun getPluginId(): PluginId = pluginId
  override fun getModuleId(): String? = null
  override fun getEdtTime(): Long = 0
  override fun getBackgroundTime(): Long = 0
  override fun getLoadedClassCount(): Long = 1
  override fun getFiles(): Collection<Path> = emptyList()
  override fun getState(): Int = classLoaderState
  override fun getPackagePrefix(): String? = null
  override fun getPluginCoroutineScope(): CoroutineScope = coroutineScope
  override fun tryLoadingClass(name: String, forceLoadFromSubPluginClassloader: Boolean): Class<*>? = loadClassInsideSelf(name)

  override fun loadClassInsideSelf(name: String): Class<*>? {
    findLoadedClass(name)?.let { return it }
    if (name != PluginVirtualFile::class.java.name) return null

    val resourceName = name.replace('.', '/') + ".class"
    val bytes = requireNotNull(parent.getResourceAsStream(resourceName)) { "Cannot find $resourceName" }.use { it.readBytes() }
    return defineClass(name, bytes, 0, bytes.size)
  }
}
