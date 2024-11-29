// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.ai

import com.intellij.collaboration.async.singleExtensionFlow
import com.intellij.collaboration.util.RefComparisonChange
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import git4idea.changes.GitTextFilePatchWithHistory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider
import javax.swing.Icon
import javax.swing.JComponent

@ApiStatus.Internal
interface GHPRAIReviewExtension {
  companion object {
    private val EP = ExtensionPointName.Companion.create<GHPRAIReviewExtension>("intellij.vcs.github.aiReviewExtension")

    internal val singleFlow: Flow<GHPRAIReviewExtension?>
      get() = EP.singleExtensionFlow()
  }

  fun provideReviewVm(project: Project, parentCs: CoroutineScope, dataContext: GHPRDataContext, dataProvider: GHPRDataProvider): GHPRAIReviewViewModel

  fun provideCommentVms(
    project: Project,
    dataProvider: GHPRDataProvider,
    change: RefComparisonChange,
    diffData: GitTextFilePatchWithHistory,
  ): Flow<List<GHPRAICommentViewModel>>

  fun createAIThread(userIcon: Icon, vm: GHPRAICommentViewModel): JComponent

  fun createAIReviewAction(project: Project, prId: GHPRIdentifier): JComponent
}