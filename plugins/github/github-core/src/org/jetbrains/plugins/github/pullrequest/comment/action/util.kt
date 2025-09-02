// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.comment.action

import com.intellij.ide.DataManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.IdeFocusManager
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHPRReviewThreadViewModel

internal fun findFocusedThreadId(project: Project): String? {
  val focusedComponent = IdeFocusManager.getInstance(project).focusOwner ?: return null
  val focusedData = DataManager.getInstance().getDataContext(focusedComponent)
  return focusedData.getData(GHPRReviewThreadViewModel.THREAD_VM_DATA_KEY)?.id
}
