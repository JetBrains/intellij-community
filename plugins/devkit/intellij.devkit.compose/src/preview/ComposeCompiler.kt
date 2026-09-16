// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.compose.preview

import androidx.compose.runtime.Composer
import com.intellij.codeInsight.AnnotationUtil
import com.intellij.debugger.ui.HotSwapUIImpl
import com.intellij.devkit.compose.hasCompose
import com.intellij.openapi.application.DevTimeClassLoader
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderEnumerator
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.compose.ComposePreviewClassLoaderProvider
import com.intellij.platform.compose.swing.ComposeSwingSearchableConfigurable
import com.intellij.psi.PsiManager
import com.intellij.task.ProjectTaskContext
import com.intellij.task.ProjectTaskManager
import com.intellij.task.impl.ProjectTaskManagerImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.jetbrains.uast.UFile
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.findSourceAnnotation
import org.jetbrains.uast.toUElement
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Files
import kotlin.coroutines.resume
import kotlin.io.path.Path

internal data class ModulePaths(val module: Module, val paths: List<String>)

internal class ComposePreviewException(cause: Throwable): RuntimeException("Unable to render Compose UI preview", cause)

internal class ComposeLocalContextException(cause: Throwable): RuntimeException(cause)

private val COMPOSITION_LOCAL_NOT_PROVIDED_PATTERN = Regex("CompositionLocal named (\\w*) not provided")

/** Which composition a preview emits into, and so which runtime mounts it. */
internal enum class PreviewKind {
  /** Emits Compose UI nodes, rendered through the Jewel bridge. */
  MULTIPLATFORM,

  /** Emits Swing components, mounted with `compose-swing-ui`. */
  SWING,
}

internal data class ContentProvider(val function: Method, val classLoader: URLClassLoader, val kind: PreviewKind) {
  fun build(currentComposer: Composer, currentCompositeKeyHashCode: Long) {
    val contextClassLoader = Thread.currentThread().contextClassLoader
    Thread.currentThread().contextClassLoader = classLoader

    try {
      function.isAccessible = true // handle private/protected visibility
      function.invoke(null, currentComposer, currentCompositeKeyHashCode.toInt())
    }
    catch (e: InvocationTargetException) {
      val original = e.cause ?: e
      if (original is IllegalStateException
          && COMPOSITION_LOCAL_NOT_PROVIDED_PATTERN.matches(original.message ?: "")) {
        throw ComposeLocalContextException(original)
      }

      throw ComposePreviewException(original)
    }
    finally {
      Thread.currentThread().contextClassLoader = contextClassLoader
    }
  }
}

internal suspend fun compileCode(fileToCompile: VirtualFile, project: Project): ContentProvider? {
  val moduleData = readAction {
    val m = ModuleUtilCore.findModuleForFile(fileToCompile, project)
    m.takeIf { hasCompose(m) }
      ?.let {
        val paths = OrderEnumerator.orderEntries(it)
          .recursively().withoutSdk().pathsList.pathList
        ModulePaths(it, paths)
      }
  } ?: return null

  val analysis = readAction {
    analyzeClass(project, fileToCompile)
  } ?: return null

  withContext(Dispatchers.EDT) {
    if (moduleData.module.isDisposed) return@withContext null
    if (!fileToCompile.isValid) return@withContext null

    compileFiles(fileToCompile, project)
  } ?: return null

  val diskPaths = moduleData.paths
    .mapNotNull { p -> Path(p).takeIf { Files.exists(it) }?.toUri()?.toURL() }
    .toTypedArray()

  for (kind in analysis.previewKinds.values.distinct()) {
    val names = analysis.previewKinds.filterValues { it == kind }.keys
    val loader = ComposeUIPreviewClassLoader(diskPaths, previewParentClassLoader(kind))
    val method = ComposableFunctionFinder(loader).findPreviewFunctions(analysis.targetClassName, names).firstOrNull()?.method
    if (method != null) return ContentProvider(method, loader, kind)
    loader.close()
  }

  return null
}

private fun previewParentClassLoader(kind: PreviewKind): ClassLoader = when (kind) {
  PreviewKind.MULTIPLATFORM -> ComposePreviewClassLoaderProvider.getClassLoader()
  PreviewKind.SWING -> ComposeSwingSearchableConfigurable::class.java.classLoader
}

internal class ComposeUIPreviewClassLoader(urls: Array<URL>, parent: ClassLoader)
  : URLClassLoader("ComposeUIPreview", urls, parent), DevTimeClassLoader

private suspend fun compileFiles(fileToCompile: VirtualFile, project: Project): ProjectTaskManager.Result? {
  val taskManager = ProjectTaskManager.getInstance(project) as ProjectTaskManagerImpl
  val task = readAction { taskManager.createModulesFilesTask(arrayOf(fileToCompile.parent)) }

  return suspendCancellableCoroutine { continuation ->
    try {
      taskManager.run(ProjectTaskContext(true).withUserData(HotSwapUIImpl.SKIP_HOT_SWAP_KEY, true), task)
        .onSuccess {
          if (it.hasErrors() || it.isAborted) {
            continuation.resume(null)
          }
          else {
            continuation.resume(it)
          }
        }
    }
    catch (e: Exception) {
      logger<ComposePreviewToolWindowFactory>().warn(e)
      continuation.resume(null)
    }
  }
}

internal data class FileAnalysisResult(
  val file: VirtualFile,
  val targetClassName: String,
  val previewKinds: Map<String, PreviewKind>,
)

private fun analyzeClass(project: Project, vFile: VirtualFile): FileAnalysisResult? {
  if (!vFile.isValid) return null

  val psiFile = PsiManager.getInstance(project).findFile(vFile)
  val uFile = psiFile?.toUElement(UFile::class.java) ?: return null

  // UFile represents the file. For Kotlin top-level functions,
  // the JVM class is still <fileName>Kt, possibly with `@file:JvmName()`
  val jvmNameAnnotation = uFile.findSourceAnnotation("kotlin.jvm.JvmName")
  val jvmName = jvmNameAnnotation?.attributeValues?.firstOrNull()?.evaluate() as? String

  val packageName = uFile.packageName
  val baseName = jvmName ?: "${vFile.nameWithoutExtension}Kt"

  val className = if (packageName.isEmpty()) baseName else "$packageName.$baseName"

  val previewKinds = uFile.classes.asSequence()
    .flatMap { it.methods.asSequence() }
    .mapNotNull { method -> previewKindOf(method)?.let { method.name to it } }
    .toMap()

  if (previewKinds.isEmpty()) return null // nothing to show here

  return FileAnalysisResult(vFile, className, previewKinds)
}

/** Which composition [method] emits into. Returns `null` when [method] is not a preview. */
private fun previewKindOf(method: UMethod): PreviewKind? = when {
  AnnotationUtil.isAnnotated(method.javaPsi, SWING_PREVIEW_ANNOTATIONS, 0) -> PreviewKind.SWING
  AnnotationUtil.isAnnotated(method.javaPsi, MULTIPLATFORM_PREVIEW_ANNOTATIONS, 0) -> PreviewKind.MULTIPLATFORM
  else -> null
}
