// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.details

import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequest
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestState
import org.jetbrains.plugins.github.ui.util.SingleValueModel

class GHPRDetailsModelImpl(private val valueModel: SingleValueModel<GHPullRequest>) : GHPRDetailsModel {

  override val number: String
    get() = valueModel.value.number.toString()
  override val title: String
    get() = valueModel.value.title
  override val description: String
    get() = valueModel.value.bodyHTML
  override val state: GHPullRequestState
    get() = valueModel.value.state
  override val isDraft: Boolean
    get() = valueModel.value.isDraft

  override fun addAndInvokeDetailsChangedListener(listener: () -> Unit) =
    valueModel.addAndInvokeValueChangedListener(listener)
}