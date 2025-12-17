// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.workspaceModel

import com.intellij.devkit.workspaceModel.codegen.writer.CodeWriter
import com.intellij.java.workspace.entities.JavaSourceRootPropertiesEntity
import com.intellij.java.workspace.entities.javaSourceRoots
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.backend.workspace.virtualFile
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.SourceRootEntity
import com.intellij.platform.workspace.jps.entities.modifyContentRootEntity
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.workspaceModel.ide.legacyBridge.findModuleEntity
import com.intellij.workspaceModel.ide.legacyBridge.findSnapshotModuleEntity
import com.intellij.workspaceModel.ide.legacyBridge.impl.java.JAVA_TEST_ROOT_ENTITY_TYPE_ID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    //val acceptedSourceRoots = getSourceRoot(module)
    val moduleEntity = module.findSnapshotModuleEntity() ?: module.findModuleEntity()
    if (moduleEntity == null) {
      LOG.info("Module entity not found")
      return
    }
    val sourceRoots = getSourceRoots(moduleEntity)
    if (sourceRoots.isEmpty()) {
      LOG.info("Acceptable module source roots not found")
      return
    }
    sourceRoots.forEach { sourceRoot ->
      if (sourceRoot.url.virtualFile == null)
        return@forEach
      withContext(Dispatchers.EDT) {
        DumbService.getInstance(project).completeJustSubmittedTasks() // Waiting for smart mode
        CodeWriter.generate(
          project, module, sourceRoot.url.virtualFile!!,
          processAbstractTypes = module.withAbstractTypes,
          explicitApiEnabled = module.explicitApiEnabled,
          isTestSourceFolder = sourceRoot.rootTypeId == JAVA_TEST_ROOT_ENTITY_TYPE_ID,
          isTestModule = module.isTestModule, // TODO(It doesn't work for all modules)
          targetFolderGenerator = {
            WriteAction.compute<VirtualFile?, Throwable> {
              var genSourceRot: SourceRootEntity? = null
              project.workspaceModel.updateProjectModel("Create gen folder for ${moduleEntity.name}") {
                genSourceRot = createGenSourceRoot(it, sourceRoot)
              }
              genSourceRot?.url?.virtualFile
            }
          },
          existingTargetFolder = {
            calculateExistingGenFolder(sourceRoot)
          },
          formatCode = true
        )
      }
    }
  }

  private fun calculateExistingGenFolder(sourceRoot: SourceRootEntity): VirtualFile? {
    val closest = sourceRoot.javaSourceRoots.firstOrNull { it.generated }
    if (closest != null) {
      return closest.sourceRoot.url.virtualFile
    }
    val contentRoot = sourceRoot.contentRoot
    val inContentRoot = contentRoot.sourceRoots.filter { it.rootTypeId == sourceRoot.rootTypeId }
      .flatMap { it.javaSourceRoots }.firstOrNull { it.generated }
    if (inContentRoot != null) {
      return inContentRoot.sourceRoot.url.virtualFile
    }
    return null
  }

  private fun createGenSourceRoot(storage: MutableEntityStorage, sourceRoot: SourceRootEntity): SourceRootEntity {
    val genFolderName = if (sourceRoot.rootTypeId == JAVA_TEST_ROOT_ENTITY_TYPE_ID) TEST_GENERATED_FOLDER_NAME else GENERATED_FOLDER_NAME
    val genFolderVirtualFile = VfsUtil.createDirectories("${sourceRoot.contentRoot.url.presentableUrl}/$genFolderName")
    val javaSourceRoot = sourceRoot.javaSourceRoots.first()
    val updatedContentRoot = storage.modifyContentRootEntity(sourceRoot.contentRoot) {
      this.sourceRoots += SourceRootEntity(genFolderVirtualFile.toVirtualFileUrl(project.workspaceModel.getVirtualFileUrlManager()),
                                           sourceRoot.rootTypeId, sourceRoot.entitySource) {
        javaSourceRoots = listOf(JavaSourceRootPropertiesEntity(true, javaSourceRoot.packagePrefix, javaSourceRoot.entitySource))
      }
    }
    val result = updatedContentRoot.sourceRoots.last()
    return result
  }

  private fun getSourceRoots(moduleEntity: ModuleEntity): List<SourceRootEntity> {
    return moduleEntity.contentRoots.flatMap { it.sourceRoots.flatMap { it.javaSourceRoots } }.filter { !it.generated }.map { it.sourceRoot }.distinct()
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
      "intellij.platform.workspace.storage.testEntities",
      "intellij.android.projectSystem.gradle"
    )

    fun getInstance(project: Project): WorkspaceModelGenerator = project.service()
  }
}