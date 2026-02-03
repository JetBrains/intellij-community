// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.create.model

import com.intellij.collaboration.util.SingleCoroutineLauncher
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import com.intellij.vcs.log.VcsCommitMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gitlab.mergerequest.ui.create.model.GitLabTitleAndDescriptionGeneratorExtension.GenerationError
import org.jetbrains.plugins.gitlab.mergerequest.ui.create.model.GitLabTitleAndDescriptionGeneratorExtension.GenerationStep

@ApiStatus.Internal
interface GitLabTitleAndDescriptionGeneratorExtension {
  companion object {
    val EP_NAME = ExtensionPointName<GitLabTitleAndDescriptionGeneratorExtension>("intellij.vcs.gitlab.titleGenerator")
  }

  sealed interface GenerationState
  data class GenerationError(val e: Exception) : GenerationState
  data class GenerationStep(val title: String, val description: String?) : GenerationState

  fun generate(project: Project, commits: List<VcsCommitMetadata>): Flow<GenerationState>
}

@ApiStatus.Internal
interface GitLabMergeRequestCreateTitleGenerationViewModel {
  companion object {
    val DATA_KEY = DataKey.create<GitLabMergeRequestCreateTitleGenerationViewModel>("GitLabCreateTitleGenerationViewModel")
  }

  val isGenerating: StateFlow<Boolean>

  fun stopGeneration()
  fun startGeneration()
}

internal class GitLabMergeRequestCreateTitleGenerationViewModelImpl(
  parentCs: CoroutineScope,
  private val project: Project,
  private val extension: GitLabTitleAndDescriptionGeneratorExtension,
  private val commits: List<VcsCommitMetadata>,
  private val setTitle: (String) -> Unit,
  private val setDescription: (String) -> Unit,
) : GitLabMergeRequestCreateTitleGenerationViewModel {
  private val taskLauncher = SingleCoroutineLauncher(parentCs.childScope("Generate Title"))
  override val isGenerating: StateFlow<Boolean> = taskLauncher.busy

  override fun stopGeneration() {
    taskLauncher.cancel()
  }

  override fun startGeneration() {
    taskLauncher.launch {
      extension.generate(project, commits).collect {
        when (it) {
          is GenerationError -> throw it.e
          is GenerationStep -> {
            setTitle(it.title)
            if (it.description != null) setDescription(it.description)
          }
        }
      }
    }
  }
}