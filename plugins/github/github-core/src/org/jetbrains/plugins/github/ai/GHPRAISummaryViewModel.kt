// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.ai

import com.intellij.collaboration.async.singleExtensionFlow
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider
import javax.swing.JComponent

/**
 * Represents a summary that can be generated at the top of the timeline.
 */
@ApiStatus.Internal
interface GHPRAISummaryViewModel {
  val isGenerating: StateFlow<Boolean>

  val summaryHtml: StateFlow<GenerationState<String>>

  val rating: StateFlow<Boolean?>
  val hasKnownActivity: StateFlow<Boolean>

  fun startGenerating()
  fun stopGenerating()

  fun rate(isUseful: Boolean)

  sealed interface GenerationState<out T> {
    data object NotStarted : GenerationState<Nothing>
    data class NotStartedDueToError(val error: @Nls String) : GenerationState<Nothing>
    data object Loading : GenerationState<Nothing>
    data class Error(val error: Exception) : GenerationState<Nothing>

    sealed interface WithValue<T> : GenerationState<T> {
      val value: T
    }

    data class Step<T>(override val value: T) : WithValue<T>
    data class Interrupted<T>(override val value: T) : WithValue<T>
    data class Done<T>(override val value: T) : WithValue<T>

    fun getOrNull(): T? = (this as? WithValue)?.value
    fun isLoading(): Boolean = this is Loading || this is Step

    fun <R> map(mapper: (T) -> R): GenerationState<R> = when (this) {
      is Step -> Step(mapper(value))
      is Interrupted -> Interrupted(mapper(value))
      is Done -> Done(mapper(value))
      is NotStarted -> this
      is NotStartedDueToError -> this
      is Loading -> this
      is Error -> this
    }
  }
}

@ApiStatus.Internal
interface GHPRAISummaryExtension {
  companion object {
    private val EP = ExtensionPointName.create<GHPRAISummaryExtension>("intellij.vcs.github.aiSummaryExtension")

    internal val singleFlow: Flow<GHPRAISummaryExtension?>
      get() = EP.singleExtensionFlow()
  }

  fun provideSummaryVm(
    cs: CoroutineScope,
    project: Project,
    dataContext: GHPRDataContext,
    dataProvider: GHPRDataProvider,
  ): GHPRAISummaryViewModel?

  fun createTimelineComponent(project: Project, vm: GHPRAISummaryViewModel): JComponent
}
