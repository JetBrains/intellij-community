// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.compose.preview

import androidx.compose.runtime.Composer
import com.intellij.codeInsight.AnnotationUtil
import com.intellij.debugger.ui.HotSwapUIImpl
import com.intellij.devkit.compose.hasCompose
import com.intellij.ide.plugins.PluginManager
import com.intellij.openapi.application.DevTimeClassLoader
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderEnumerator
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.task.ProjectTaskContext
import com.intellij.task.ProjectTaskManager
import com.intellij.task.impl.ProjectTaskManagerImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.jetbrains.uast.UFile
import org.jetbrains.uast.findSourceAnnotation
import org.jetbrains.uast.toUElement
import java.lang.reflect.Method
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Files
import kotlin.coroutines.resume
import kotlin.io.path.Path

internal data class ModulePaths(val module: Module, val paths: List<String>)

internal data class ContentProvider(val function: Method, val classLoader: URLClassLoader) {
  fun build(currentComposer: Composer, currentCompositeKeyHashCode: Long) {
    val contextClassLoader = Thread.currentThread().contextClassLoader
    Thread.currentThread().contextClassLoader = classLoader

    try {
      function.invoke(null, currentComposer, currentCompositeKeyHashCode.toInt())
    }
    catch (t: Throwable) {
      thisLogger().warn("Unable to build preview", t)
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

  val compiled = withContext(Dispatchers.EDT) {
    if (moduleData.module.isDisposed) return@withContext emptyList()
    if (!fileToCompile.isValid) return@withContext emptyList()

    val files = compileFiles(fileToCompile, project)
    files
  }

  if (compiled.isEmpty()) return null

  val diskPaths = moduleData.paths
    .mapNotNull { p -> Path(p).takeIf { Files.exists(it) }?.toUri()?.toURL() }
    .toTypedArray()

  val pluginByClass = PluginManager.getPluginByClass(ComposePreviewToolWindowFactory::class.java)
  val filteringClassLoader = FilteringClassLoader(pluginByClass!!.classLoader)

  val loader = ComposeUIPreviewClassLoader(diskPaths, filteringClassLoader)
  val functions = ComposableFunctionFinder(loader).findPreviewFunctions(analysis.targetClassName, analysis.composableMethodNames)

  return functions.firstOrNull()?.method
    ?.let { ContentProvider(it, loader) }
}

internal class ComposeUIPreviewClassLoader(urls: Array<URL>, parent: ClassLoader)
  : URLClassLoader("ComposeUIPreview", urls, parent), DevTimeClassLoader

private suspend fun compileFiles(fileToCompile: VirtualFile, project: Project): List<VirtualFile> {
  val taskManager = ProjectTaskManager.getInstance(project) as ProjectTaskManagerImpl
  val task = readAction { taskManager.createModulesFilesTask(arrayOf(fileToCompile.parent)) }

  return suspendCancellableCoroutine { continuation ->
    try {
      taskManager.run(ProjectTaskContext(true).withUserData(HotSwapUIImpl.SKIP_HOT_SWAP_KEY, true), task)
        .onSuccess {
          if (it.hasErrors() || it.isAborted) {
            continuation.resume(emptyList())
          }
          else {
            continuation.resume(listOf(fileToCompile))
          }
        }
    }
    catch (e: Exception) {
      logger<ComposePreviewToolWindowFactory>().warn(e)
      continuation.resume(emptyList())
    }
  }
}

internal data class FileAnalysisResult(
  val file: VirtualFile,
  val targetClassName: String,
  val composableMethodNames: Collection<String>,
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

  val annotatedMethodNames = uFile.classes.asSequence()
    .flatMap { it.methods.asSequence() }
    .filter {
      AnnotationUtil.isAnnotated(it.javaPsi, PREVIEW_ANNOTATIONS, 0)
      || it.uAnnotations.any { a -> a.qualifiedName?.endsWith(".Preview") == true }
    }
    .map { it.name }
    .toSet()

  return FileAnalysisResult(vFile, className, annotatedMethodNames)
}

/**
 * Isolates project code from attempts to load unrelated classes via the parent classloader.
 */
private class FilteringClassLoader(parent: ClassLoader) : ClassLoader(parent) {
  override fun loadClass(name: String, resolve: Boolean): Class<*>? {
    if (isAlienClass(name)) throw ClassNotFoundException(name)

    return super.loadClass(name, resolve)
  }

  override fun findClass(name: String): Class<*> {
    if (isAlienClass(name)) throw ClassNotFoundException(name)

    return super.findClass(name)
  }

  private fun isAlienClass(name: String): Boolean {
    return name.startsWith("com.intellij.") && !name.startsWith("com.intellij.openapi.")
  }
}