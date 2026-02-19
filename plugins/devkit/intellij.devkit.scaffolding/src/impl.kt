// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.scaffolding

import com.intellij.ide.ui.newItemPopup.NewItemPopupUtil
import com.intellij.ide.ui.newItemPopup.NewItemSimplePopupPanel
import com.intellij.lang.LangBundle
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.backgroundWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.IntelliJProjectUtil.isIntelliJPlatformProject
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Consumer
import com.intellij.workspaceModel.ide.legacyBridge.impl.java.JAVA_MODULE_ENTITY_TYPE_ID_NAME
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.Nls
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootType
import java.awt.event.InputEvent
import java.nio.file.Path
import javax.swing.JTextField
import kotlin.coroutines.resume
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.writeText

internal fun isActionEnabled(dc: DataContext): Boolean {
  val project = dc.getData(CommonDataKeys.PROJECT)
                ?: return false
  val virtualFile = dc.getData(CommonDataKeys.VIRTUAL_FILE)
                    ?: return false
  if (!isIntelliJPlatformProject(project)) {
    return false
  }
  return !ProjectFileIndex.getInstance(project).isInSource(virtualFile)
}

internal suspend fun askForModuleName(project: Project, suggestedNamePrefix: String): @Nls String {
  val contentPanel = NewItemSimplePopupPanel()
  val nameField: JTextField = contentPanel.textField
  nameField.text = suggestedNamePrefix
  val popup: JBPopup = NewItemPopupUtil.createNewItemPopup(message("scaffolding.new.ij.module"), contentPanel, nameField)
  return suspendCancellableCoroutine { continuation ->
    contentPanel.applyAction = Consumer { event: InputEvent? ->
      val name = nameField.text
      if (name.isBlank()) {
        // TODO validation
        contentPanel.setError(LangBundle.message("incorrect.name"))
      }
      else {
        popup.closeOk(event)
        continuation.resume(name)
      }
    }
    popup.addListener(object : JBPopupListener {
      override fun onClosed(event: LightweightWindowEvent) {
        if (!event.isOk) {
          continuation.cancel()
        }
      }
    })
    continuation.invokeOnCancellation {
      popup.cancel()
    }
    popup.showCenteredInCurrentWindow(project)
  }
}

internal suspend fun suggestModuleNamePrefix(parentDir: VirtualFile, project: Project): String {
  return readAction {
    val fileIndex = ProjectFileIndex.getInstance(project)
    val parentContentRoot = fileIndex.getContentRootForFile(parentDir)
    if (parentContentRoot == parentDir) {
      val module = fileIndex.getModuleForFile(parentContentRoot)
      if (module != null) {
        return@readAction "${module.name.removeSuffix(".plugin").removeSuffix(".main").removeSuffix(".core")}."
      }
    }
    if (parentDir.isDirectory) {
      val siblingModuleNames = parentDir.children
        .asSequence()
        .filter { fileIndex.getContentRootForFile(it) == it }
        .mapNotNull { fileIndex.getModuleForFile(it)?.name }
      val commonPrefix = siblingModuleNames.reduceOrNull { acc, item -> acc.commonPrefixWith(item) } ?: ""
      val suggestion = commonPrefix.substringBeforeLast('.', "")
      if (suggestion.isNotEmpty()) {
        return@readAction "$suggestion."
      }
    }
    "intellij."
  }
}

internal suspend fun createIjModule(project: Project, vRoot: VirtualFile, moduleName: String) {
  val directoryName = computeDirectoryNameForModule(moduleName, vRoot, project)
  val files = prepareFiles(vRoot.toNioPath(), moduleName, directoryName)
  val vFiles = files.toVFiles()
               ?: return // TODO remove when failed
  backgroundWriteAction {
    val module = ModuleManager.getInstance(project).newModule(files.moduleRoot.resolve("$moduleName.iml"), JAVA_MODULE_ENTITY_TYPE_ID_NAME)
    val rootModel = ModuleRootManager.getInstance(module).modifiableModel
    rootModel.inheritSdk()
    rootModel.addContentEntry(vFiles.vModuleRoot).also { contentEntry ->
      contentEntry.addSourceFolder(vFiles.vSrc, JavaSourceRootType.SOURCE).also {
        it.packagePrefix = "com.$moduleName"
      }
      contentEntry.addSourceFolder(vFiles.vResources, JavaResourceRootType.RESOURCE)
    }
    rootModel.commit()
  }
  project.scheduleSave() // to write changes in modules.xml to the disk
}

private suspend fun computeDirectoryNameForModule(moduleName: String, parentDir: VirtualFile, project: Project): String {
  return withContext(Dispatchers.IO) {
    readAction {
      val fileIndex = ProjectFileIndex.getInstance(project)
      val parentDirectoryNames = generateSequence(parentDir) { it.parent }
        .takeWhile { it.parent != null && fileIndex.isInProjectOrExcluded(it.parent) }
        .mapTo(HashSet()) { it.name }
      moduleName.split('.').dropWhile { it in parentDirectoryNames || it == "intellij" }.joinToString(".")
    }
  }
}

private suspend fun prepareFiles(root: Path, moduleName: String, directoryName: String): ModuleFiles = withContext(Dispatchers.IO) {
  val moduleRoot = root.resolve(directoryName).createDirectories()
  val src = moduleRoot.resolve("src").createDirectories().also { src ->
    src.resolve(".keep").createFile() // empty file so that `src` exists in git
  }
  val resources = moduleRoot.resolve("resources").createDirectories().also { resources ->
    resources.resolve("$moduleName.xml").writeText("<idea-plugin>\n</idea-plugin>\n")
  }
  ModuleFiles(moduleRoot, src, resources)
}

private class ModuleFiles(
  val moduleRoot: Path,
  val src: Path,
  val resources: Path,
)

private suspend fun ModuleFiles.toVFiles(): ModuleVFiles? = withContext(Dispatchers.IO) {
  ModuleVFiles(
    vModuleRoot = VfsUtil.findFile(moduleRoot, true) ?: return@withContext null,
    vSrc = VfsUtil.findFile(src, true) ?: return@withContext null,
    vResources = VfsUtil.findFile(resources, true) ?: return@withContext null,
  )
}

private class ModuleVFiles(
  val vModuleRoot: VirtualFile,
  val vSrc: VirtualFile,
  val vResources: VirtualFile,
)
