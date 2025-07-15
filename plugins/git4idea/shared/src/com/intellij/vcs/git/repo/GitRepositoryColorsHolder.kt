// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.repo

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.platform.project.projectId
import com.intellij.platform.vcs.impl.shared.rpc.RepositoryId
import com.intellij.vcs.git.repo.GitRepositoryColor.Companion.toAwtColor
import com.intellij.vcs.git.rpc.GitRepositoryColorsApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import java.awt.Color

@ApiStatus.Internal
@Serializable
data class GitRepositoryColorsState(
  val colors: Map<RepositoryId, GitRepositoryColor> = emptyMap(),
)

/**
 * Bring both bright and dark colors not to handle LaF changes
 */
@JvmInline
@ApiStatus.Internal
@Serializable
value class GitRepositoryColor(val rgb: Int) {
  companion object {
    fun of(color: Color): GitRepositoryColor = GitRepositoryColor(color.rgb)

    fun GitRepositoryColor.toAwtColor(): Color = Color(rgb)
  }
}

@Service(Service.Level.PROJECT)
internal class GitRepositoryColorsHolder(project: Project, cs: CoroutineScope) {
  private var state = GitRepositoryColorsState()

  init {
    cs.launch {
      GitRepositoryColorsApi.getInstance().syncColors(project.projectId()).collect { newState ->
        LOG.debug("Received new colors - ${newState.colors.size} repos")
        state = newState
      }
    }
  }

  fun getColor(repositoryId: RepositoryId): Color? {
    val repoColor = state.colors[repositoryId]

    if (repoColor == null) {
      LOG.warn("Color for $repositoryId not loaded")
      return null
    }

    return repoColor.toAwtColor()
  }

  companion object {
    private val LOG = Logger.getInstance(GitRepositoryColorsHolder::class.java)

    fun getInstance(project: Project): GitRepositoryColorsHolder = project.service()
  }
}