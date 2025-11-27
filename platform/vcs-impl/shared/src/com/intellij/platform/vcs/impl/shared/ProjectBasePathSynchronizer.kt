// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.shared

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectCustomDataSynchronizer
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.actions.VcsContextFactory
import com.intellij.platform.vcs.impl.shared.rpc.FilePathDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import org.jetbrains.annotations.SystemIndependent
import java.util.concurrent.atomic.AtomicReference
import kotlin.reflect.KType
import kotlin.reflect.typeOf

/**
 * In the case of Thin Client, [Project.getBasePath] returns a path to the project configuration storage on the client.
 * However, we would like to use the host's path to display relative paths in the UI.
 */
internal class ProjectBasePathSynchronizer : ProjectCustomDataSynchronizer<FilePathDto> {
  override val id: String = "vcs.projectDir"

  override val dataType: KType = typeOf<FilePathDto>()

  override fun getValues(project: Project): Flow<FilePathDto> {
    val guessedDir = project.guessProjectDir() ?: return emptyFlow()
    val projectFilePath = VcsContextFactory.getInstance().createFilePathOn(guessedDir)
    return flowOf(FilePathDto.toDto(projectFilePath))
  }

  override suspend fun consumeValue(project: Project, value: FilePathDto) {
    project.serviceAsync<ProjectBasePathHolder>().consumePresentablePath(value.filePath)
  }
}

@Service(Service.Level.PROJECT)
internal class ProjectBasePathHolder(private val project: Project) {
  private val presentablePath = AtomicReference<FilePath?>()

  fun getPresentablePath(): @SystemIndependent String? {
    val path = presentablePath.get()
    return path?.presentableUrl ?: guessProjectDirAndCache()
  }

  private fun guessProjectDirAndCache(): @NlsSafe String? {
    val projectDir = project.guessProjectDir() ?: return null
    val newValue = VcsContextFactory.getInstance().createFilePathOn(projectDir)
    presentablePath.compareAndSet(null, newValue)
    return newValue.presentableUrl
  }

  fun consumePresentablePath(presentablePath: FilePath) {
    this.presentablePath.set(presentablePath)
  }
}
