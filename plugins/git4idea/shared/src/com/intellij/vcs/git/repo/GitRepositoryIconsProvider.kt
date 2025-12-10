// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.repo

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.platform.project.projectId
import com.intellij.platform.vcs.impl.shared.rpc.RepositoryId
import com.intellij.util.PlatformIcons
import com.intellij.util.ui.CheckboxIcon
import com.intellij.vcs.git.repo.GitRepositoryColor.Companion.toAwtColor
import com.intellij.vcs.git.rpc.GitRepositoryColorsApi
import fleet.rpc.client.durable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import javax.swing.Icon

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

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class GitRepositoryIconsProvider(project: Project, cs: CoroutineScope) {
  private var state = GitRepositoryColorsState()

  init {
    cs.launch {
      durable {
        GitRepositoryColorsApi.getInstance().syncColors(project.projectId()).collect { newState ->
          LOG.debug("Received new colors - ${newState.colors.size} repos")
          state = newState
        }
      }
    }
  }

  fun getIcon(repositoryId: RepositoryId): Icon {
    val color = getColor(repositoryId)
    return if (color != null) CheckboxIcon.createAndScale(color) else PlatformIcons.FOLDER_ICON
  }

  private fun getColor(repositoryId: RepositoryId): Color? {
    val repoColor = state.colors[repositoryId]

    if (repoColor == null) {
      LOG.warn("Color for $repositoryId not loaded")
      return null
    }

    return repoColor.toAwtColor()
  }

  companion object {
    private val LOG = Logger.getInstance(GitRepositoryIconsProvider::class.java)

    fun getInstance(project: Project): GitRepositoryIconsProvider = project.service()
  }
}