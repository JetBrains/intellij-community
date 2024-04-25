// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.resources.providers.module

import com.intellij.openapi.application.writeAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.testFramework.junit5.resources.providers.ParameterizableResourceProvider
import com.intellij.testFramework.junit5.resources.providers.PathInfo.Companion.addPathInfoToDeleteOnExit
import com.intellij.testFramework.junit5.resources.providers.ResourceStorage
import com.intellij.testFramework.junit5.resources.providers.module.ModulePersistenceType.NonPersistent
import com.intellij.testFramework.junit5.resources.providers.module.ModulePersistenceType.Persistent
import com.intellij.testFramework.junit5.resources.providers.module.ProjectSource.ExplicitProject
import com.intellij.testFramework.junit5.resources.providers.module.ProjectSource.ProjectFromExtension
import com.intellij.testFramework.junit5.resources.providers.ProjectProvider
import org.jetbrains.annotations.TestOnly
import kotlin.reflect.KClass

/**
 * Creates [Module].
 * Depends on [ProjectProvider] as it needs a project, unless [ProjectSource.ExplicitProject].
 * [createDefaultParams] must not return same name on each call as two modules can't have the same name.
 */
@TestOnly
class ModuleProvider(private val createDefaultParams: () -> ModuleParams = { ModuleParams() }) : ParameterizableResourceProvider<Module, ModuleParams> {
  override val resourceType: KClass<Module> = Module::class
  override val needsApplication: Boolean = true

  private companion object {

    @TestOnly
    private fun getProject(storage: ResourceStorage, projectSource: ProjectSource): Project =
      when (projectSource) {
        is ExplicitProject -> projectSource.project
        ProjectFromExtension -> storage.getResourceCreatedByProvider(ProjectProvider::class, Project::class).getOrThrow()
      }
  }

  override suspend fun create(storage: ResourceStorage, params: ModuleParams): Module {
    val project = getProject(storage, params.projectSource)
    val manager = ModuleManager.getInstance(project)
    return writeAction {
      when (val t = params.modulePersistenceType) {
        is NonPersistent -> manager.newNonPersistentModule(params.name.name, params.moduleTypeId)
        is Persistent -> {
          val pathInfo = t.pathGetter(project, params.name)
          manager.newModule(pathInfo.path, params.moduleTypeId).also {
            it.addPathInfoToDeleteOnExit(pathInfo)
          }
        }
      }
    }
  }

  override suspend fun create(storage: ResourceStorage): Module = create(storage, createDefaultParams())

  override suspend fun destroy(resource: Module) {
    writeAction {
      ModuleManager.getInstance(resource.project).disposeModule(resource)
    }
  }
}