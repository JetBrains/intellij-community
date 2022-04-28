// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.codegen

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.SourceFolder
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.jps.model.java.JavaSourceRootProperties
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.java.JpsJavaExtensionService

private val LOG = logger<WorkspaceModelGenerationAction>()

class WorkspaceModelGenerationAction: AnAction() {
  override fun actionPerformed(event: AnActionEvent) {
    val project = event.project ?: return
    val module = event.getData(PlatformCoreDataKeys.MODULE) ?: return
    val virtualFiles = event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) ?: return
    if (virtualFiles.size != 1 || !virtualFiles[0].isDirectory) return
    val selectedFolder = virtualFiles[0]

    val acceptedSourceRoots = getSourceRoot(module, selectedFolder)
    if (acceptedSourceRoots.isEmpty()) {
      LOG.info("Acceptable module source roots not found")
      return
    }
    acceptedSourceRoots.forEach{ sourceRoot ->
      val generatedSourceFolder = createGeneratedSourceFolder(module, sourceRoot)
      if (generatedSourceFolder == null) {
        LOG.info("Generated source folder doesn't exist. Skip processing source folder with path: ${sourceRoot.file}")
        return@forEach
      }
      WriteAction.run<RuntimeException> { CodeWriter.generate(project, selectedFolder, generatedSourceFolder) }
    }
    println("Selected module ${module.name}")
  }

  private fun createGeneratedSourceFolder(module: Module, sourceFolder: SourceFolder): VirtualFile? {
    // Create gen folder if it doesn't exist
    val generatedFolder = WriteAction.compute<VirtualFile, RuntimeException> {
      VfsUtil.createDirectoryIfMissing(sourceFolder.file?.parent, GENERATED_FOLDER_NAME)
    }

    val moduleRootManager = ModuleRootManager.getInstance(module)
    val modifiableModel = moduleRootManager.modifiableModel
    // Searching for the related content root
    for (contentEntry in modifiableModel.contentEntries) {
      val contentEntryFile = contentEntry.file
      if (contentEntryFile != null && VfsUtilCore.isAncestor(contentEntryFile, generatedFolder, false)) {
        // Checking if it already contains generation folder
        val existingGenFolder = contentEntry.sourceFolders.firstOrNull {
          (it.jpsElement.properties as? JavaSourceRootProperties)?.isForGeneratedSources == true &&
          it.file == generatedFolder
        }
        if (existingGenFolder != null) return generatedFolder
        // If it doesn't, create new get folder for the selected content root
        val properties = JpsJavaExtensionService.getInstance().createSourceRootProperties("", true)
        val sourceFolderType = if (sourceFolder.isTestSource) JavaSourceRootType.TEST_SOURCE else JavaSourceRootType.SOURCE
        contentEntry.addSourceFolder(generatedFolder, sourceFolderType, properties)
        WriteAction.run<RuntimeException> {
          modifiableModel.commit()
          module.project.save()
        }
        return generatedFolder
      }
    }
    modifiableModel.dispose()
    return null
  }

  private fun getSourceRoot(module: Module, selectedFolder: VirtualFile): List<SourceFolder> {
    val moduleRootManager = ModuleRootManager.getInstance(module)
    val contentEntries = moduleRootManager.contentEntries
    if (contentEntries.size != 1) {
      LOG.info("Unsupported cont of content roots for the module ${module.name}. Expected: 1, actual: ${contentEntries.size}")
      return emptyList()
    }
    val contentEntry = contentEntries[0]
    //val contentEntryFile = contentEntry.file
    //val sourceFolders = contentEntry.sourceFolders
    //  if (contentEntryFile != null && VfsUtilCore.isAncestor(contentEntryFile, selectedFolder, false)) {
    //    val sourceFolder = contentEntry.sourceFolders.firstOrNull { sourceRoot -> VfsUtil.isUnder(selectedFolder, setOf(sourceRoot.file)) }
    //    if (sourceFolder != null) return sourceFolder
    //  }
    //}
    return contentEntry.sourceFolders.filter {
      if (it.file == null)  return@filter false
      val javaSourceRootProperties = it.jpsElement.properties as? JavaSourceRootProperties
      if (javaSourceRootProperties == null) return@filter true
      return@filter !javaSourceRootProperties.isForGeneratedSources
    }
  }

  companion object {
    private const val GENERATED_FOLDER_NAME = "gen"
  }
}