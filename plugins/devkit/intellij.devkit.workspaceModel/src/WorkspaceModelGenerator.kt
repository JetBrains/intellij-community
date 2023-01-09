// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.workspaceModel

import com.intellij.devkit.workspaceModel.codegen.writer.CodeWriter
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.SourceFolder
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.jps.model.java.JavaSourceRootProperties
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.java.JpsJavaExtensionService

private val LOG = logger<WorkspaceModelGenerator>()

object WorkspaceModelGenerator {
  const val GENERATED_FOLDER_NAME = "gen"

  fun generate(project: Project, module: Module) {
    val acceptedSourceRoots = getSourceRoot(module)
    if (acceptedSourceRoots.isEmpty()) {
      LOG.info("Acceptable module source roots not found")
      return
    }
    acceptedSourceRoots.forEach{ sourceRoot ->
      WriteAction.run<RuntimeException> {
        CodeWriter.generate(project, module, sourceRoot.file!!) {
          createGeneratedSourceFolder(module, sourceRoot)
        }
      }
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
        WriteAction.run<RuntimeException>(modifiableModel::commit)
        return generatedFolder
      }
    }
    modifiableModel.dispose()
    return null
  }

  private fun getSourceRoot(module: Module): List<SourceFolder> {
    val moduleRootManager = ModuleRootManager.getInstance(module)
    val contentEntries = moduleRootManager.contentEntries
    //val contentEntryFile = contentEntry.file
    //val sourceFolders = contentEntry.sourceFolders
    //  if (contentEntryFile != null && VfsUtilCore.isAncestor(contentEntryFile, selectedFolder, false)) {
    //    val sourceFolder = contentEntry.sourceFolders.firstOrNull { sourceRoot -> VfsUtil.isUnder(selectedFolder, setOf(sourceRoot.file)) }
    //    if (sourceFolder != null) return sourceFolder
    //  }
    //}
    return contentEntries.flatMap { it.sourceFolders.asIterable() }.filter {
      if (it.file == null) return@filter false
      val javaSourceRootProperties = it.jpsElement.properties as? JavaSourceRootProperties
      if (javaSourceRootProperties == null) return@filter true
      return@filter !javaSourceRootProperties.isForGeneratedSources
    }
  }
}