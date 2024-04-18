// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.resources.providers

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.testFramework.junit5.impl.createTempDirectory
import com.intellij.testFramework.junit5.resources.providers.PathInfo.Companion.addPathInfoToDeleteOnExit
import org.jetbrains.annotations.TestOnly
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import kotlin.reflect.KClass

/**
 * Creates [Project]
 */
@TestOnly
class ProjectProvider(private val createDefaultProjectLocation: suspend () -> PathInfo) : ParameterizableResourceProvider<Project, PathInfo> {
  constructor(fs: FileSystem = FileSystems.getDefault(), closeFsOnExit: Boolean = false) : this({ PathInfo(createTempDirectory(fs), closeFsOnExit = closeFsOnExit) })

  override val resourceType: KClass<Project> = Project::class

  override val needsApplication: Boolean = true

  override suspend fun destroy(resource: Project) {
    ProjectManagerEx.getInstanceEx().forceCloseProjectAsync(resource, save = false)
  }

  override suspend fun create(storage: ResourceStorage): Project = create(storage, createDefaultProjectLocation())

  override suspend fun create(storage: ResourceStorage, params: PathInfo): Project {
    return ProjectManagerEx.getInstanceEx().newProjectAsync(params.path, OpenProjectTask(isNewProject = true)).also {
      it.addPathInfoToDeleteOnExit(params)
    }
  }
}