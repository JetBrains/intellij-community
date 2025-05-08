// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.workspaceModel

import com.intellij.devkit.workspaceModel.codegen.writer.CodeWriter
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.SourceFolder
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.jps.model.java.JavaSourceRootProperties
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.kotlin.config.ExplicitApiMode
import org.jetbrains.kotlin.config.IKotlinFacetSettings
import org.jetbrains.kotlin.config.additionalArgumentsAsList
import org.jetbrains.kotlin.idea.base.facet.isTestModule
import org.jetbrains.kotlin.idea.facet.KotlinFacet

private val LOG = logger<WorkspaceModelGenerator>()

@Service(Service.Level.PROJECT)
class WorkspaceModelGenerator(private val project: Project, private val coroutineScope: CoroutineScope) {

  fun generate(module: Module) {
    LOG.info("Selected module ${module.name}")
    coroutineScope.launch {
      onModule(module)
    }
  }

  fun generate(modules: Array<Module>) {
    coroutineScope.launch {
      modules.forEachIndexed { index, module ->
        LOG.info("Updating ${module.name}, $index")
        onModule(module)
      }
    }
  }

  private suspend fun onModule(module: Module) {
    val acceptedSourceRoots = getSourceRoot(module)
    if (acceptedSourceRoots.isEmpty()) {
      LOG.info("Acceptable module source roots not found")
      return
    }
    acceptedSourceRoots.forEach { sourceRoot ->
      withContext(Dispatchers.EDT) {
        DumbService.getInstance(project).completeJustSubmittedTasks() // Waiting for smart mode
        CodeWriter.generate(
          project, module, sourceRoot.file!!,
          processAbstractTypes = module.withAbstractTypes,
          explicitApiEnabled = module.explicitApiEnabled,
          isTestSourceFolder = sourceRoot.isTestSource,
          isTestModule = module.isTestModule, // TODO(It doesn't work for all modules)
          targetFolderGenerator = { createGeneratedSourceFolder(module, sourceRoot) },
          existingTargetFolder = { calculateExistingGenFolder(sourceRoot) }
        )
      }
    }
  }

  private fun calculateExistingGenFolder(sourceFolder: SourceFolder): VirtualFile? {
    if (sourceFolder.contentEntry.file == sourceFolder.file) return null
    return sourceFolder.file?.parent?.children?.find {
      if (sourceFolder.isTestSource) {
        it.name == TEST_GENERATED_FOLDER_NAME
      } else {
        it.name == GENERATED_FOLDER_NAME
      }
    }
  }

  private fun createGeneratedSourceFolder(module: Module, sourceFolder: SourceFolder): VirtualFile? {
    // Create gen folder if it doesn't exist
    val generatedFolder = WriteAction.compute<VirtualFile, RuntimeException> {
      if (sourceFolder.isTestSource) {
        VfsUtil.createDirectoryIfMissing(sourceFolder.file?.parent, TEST_GENERATED_FOLDER_NAME)
      } else {
        VfsUtil.createDirectoryIfMissing(sourceFolder.file?.parent, GENERATED_FOLDER_NAME)
      }
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
      if (it.rootType !is JavaSourceRootType)  return@filter false
      val javaSourceRootProperties = it.jpsElement.properties as? JavaSourceRootProperties
      if (javaSourceRootProperties == null) return@filter true
      return@filter !javaSourceRootProperties.isForGeneratedSources
    }
  }

  private val Module.withAbstractTypes: Boolean
    get() = name in modulesWithAbstractTypes || name.startsWith(RIDER_MODULES_PREFIX)

  private val Module.explicitApiEnabled: Boolean
    get() {
      val facetSettings: IKotlinFacetSettings? = KotlinFacet.get(this)?.configuration?.settings
      val compilerArguments = facetSettings?.compilerSettings?.additionalArgumentsAsList
      return compilerArguments?.contains("-Xexplicit-api=${ExplicitApiMode.STRICT.state}") == true
    }

  companion object {
    const val GENERATED_FOLDER_NAME: String = "gen"

    const val TEST_GENERATED_FOLDER_NAME: String = "testGen"

    const val RIDER_MODULES_PREFIX: String = "intellij.rider"

    val modulesWithAbstractTypes: Set<String> = setOf(
      "intellij.platform.workspace.storage.testEntities"
    )

    fun getInstance(project: Project): WorkspaceModelGenerator = project.service()
  }
}