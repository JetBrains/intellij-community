// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.compose.preview

import androidx.compose.runtime.Composer
import com.intellij.debugger.ui.HotSwapUIImpl
import com.intellij.devkit.compose.hasCompose
import com.intellij.ide.plugins.PluginManager
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
import java.net.URLClassLoader
import java.nio.file.Files
import kotlin.coroutines.resume
import kotlin.io.path.Path

internal data class ModulePaths(val module: Module, val paths: List<String>)

internal data class ContentProvider(val function: Method, val classLoader: URLClassLoader) {
  fun build(currentComposer: Composer, currentCompositeKeyHashCode: Long) {
    try {
      function.invoke(null, currentComposer, currentCompositeKeyHashCode.toInt())
    }
    catch (t: Throwable) {
      thisLogger().warn("Unable to build preview", t)
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

  val clazzFqn = readAction {
    getKotlinFileJvmClassFqnViaUast(project, fileToCompile)
  } ?: return null

  return withContext(Dispatchers.EDT) {
    if (moduleData.module.isDisposed) return@withContext null
    if (!fileToCompile.isValid) return@withContext null

    val files = compileFiles(fileToCompile, project)
    if (files.isEmpty()) return@withContext null

    val diskPaths = moduleData.paths
      .mapNotNull { p -> Path(p).takeIf { Files.exists(it) }?.toUri()?.toURL() }
      .toTypedArray()

    val pluginByClass = PluginManager.getPluginByClass(ComposePreviewToolWindowFactory::class.java)
    val parent = pluginByClass!!.classLoader
    val loader = URLClassLoader("ComposePreview", diskPaths, parent)
    val functions = ComposableFunctionFinder(loader)
      .findPreviewFunctions(clazzFqn)

    functions.firstOrNull()?.method
      ?.let { ContentProvider(it, loader) }
    ?: return@withContext null
  }
}

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

private fun getKotlinFileJvmClassFqnViaUast(project: Project, vFile: VirtualFile): String? {
  val psiFile = PsiManager.getInstance(project).findFile(vFile) ?: return null

  val uFile = psiFile.toUElement(UFile::class.java) ?: return null

  // UFile represents the file. For Kotlin top-level functions,
  // the JVM class is still <fileName>Kt, possibly with `@file:JvmName()`
  val jvmNameAnnotation = uFile.findSourceAnnotation("kotlin.jvm.JvmName")
  val jvmName = jvmNameAnnotation?.attributeValues?.firstOrNull()?.evaluate() as? String

  val packageName = uFile.packageName
  val baseName = jvmName ?: "${vFile.nameWithoutExtension}Kt"

  return if (packageName.isEmpty()) baseName else "$packageName.$baseName"
}