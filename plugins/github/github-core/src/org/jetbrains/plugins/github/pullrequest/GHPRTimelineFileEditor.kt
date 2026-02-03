// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest

import com.intellij.collaboration.async.cancelledWith
import com.intellij.diff.util.FileEditorBase
import com.intellij.openapi.application.EDT
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.ui.GHPRConnectedProjectViewModel
import javax.swing.JComponent

internal class GHPRTimelineFileEditor(parentCs: CoroutineScope,
                                      private val projectVm: GHPRConnectedProjectViewModel,
                                      private val file: GHPRTimelineVirtualFile)
  : FileEditorBase() {
  private val cs = parentCs
    .childScope("GitHub Pull Request Timeline UI", Dispatchers.EDT)
    .cancelledWith(this)

  private val timelineVm = projectVm.acquireTimelineViewModel(file.pullRequest, cs)

  override fun getName() = GithubBundle.message("pull.request.editor.timeline")

  private val content by lazy(LazyThreadSafetyMode.NONE) {
    GHPRTimelineComponentFactory.create(file.project, cs, projectVm, timelineVm, file.pullRequest)
  }

  override fun getComponent() = content

  override fun getPreferredFocusedComponent(): JComponent? = null

  override fun selectNotify() {
    timelineVm.update()
  }

  override fun getFile() = file
}