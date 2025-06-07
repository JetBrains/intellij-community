// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow.create

import com.intellij.collaboration.util.SingleCoroutineLauncher
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import com.intellij.vcs.log.VcsCommitMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.create.GHPRTitleAndDescriptionGeneratorExtension.*

@ApiStatus.Internal
interface GHPRTitleAndDescriptionGeneratorExtension {
  companion object {
    val EP_NAME = ExtensionPointName<GHPRTitleAndDescriptionGeneratorExtension>("intellij.vcs.github.titleAndDescriptionGenerator")
  }

  sealed interface GenerationState
  data class GenerationError(val e: Exception) : GenerationState
  data class GenerationStep(val title: String, val description: String?) : GenerationState
  data class GenerationDone(val score: (helpfulYesOrNo: Boolean) -> Unit) : GenerationState

  fun generate(project: Project, commits: List<VcsCommitMetadata>, template: String?): Flow<GenerationState>
  suspend fun onGenerationDone(project: Project, editor: Editor, score: (Boolean) -> Unit)
}

@ApiStatus.Internal
interface GHPRCreateTitleAndDescriptionGenerationViewModel {
  companion object {
    val DATA_KEY = DataKey.create<GHPRCreateTitleAndDescriptionGenerationViewModel>("GHPRCreateTitleAndDescriptionGenerationViewModel")
  }

  val isGenerating: StateFlow<Boolean>
  val generationFeedbackActivity: SharedFlow<(helpfulYesOrNo: Boolean) -> Unit>

  fun stopGeneration()
  fun startGeneration()
}

internal class GHPRCreateTitleAndDescriptionGenerationViewModelImpl(
  parentCs: CoroutineScope,
  private val project: Project,
  private val extension: GHPRTitleAndDescriptionGeneratorExtension,
  private val commits: List<VcsCommitMetadata>,
  private val template: String?,
  private val setTitle: (String) -> Unit,
  private val setDescription: (String) -> Unit,
) : GHPRCreateTitleAndDescriptionGenerationViewModel {
  private val taskLauncher = SingleCoroutineLauncher(parentCs.childScope("Generate Title and Description"))
  override val isGenerating: StateFlow<Boolean> = taskLauncher.busy

  private val _generationFeedbackActivity = MutableSharedFlow<(helpfulYesOrNo: Boolean) -> Unit>()
  override val generationFeedbackActivity: SharedFlow<(helpfulYesOrNo: Boolean) -> Unit> = _generationFeedbackActivity

  override fun stopGeneration() {
    taskLauncher.cancel()
  }

  override fun startGeneration() {
    taskLauncher.launch {
      extension.generate(project, commits, template).collect {
        when (it) {
          is GenerationError -> throw it.e
          is GenerationStep -> {
            setTitle(it.title)
            if (it.description != null) setDescription(it.description)
          }
          is GenerationDone -> {
            _generationFeedbackActivity.emit(it.score)
          }
        }
      }
    }
  }
}