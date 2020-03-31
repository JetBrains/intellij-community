// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.details

import com.intellij.ui.components.panels.NonOpaquePanel
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequest
import org.jetbrains.plugins.github.ui.util.HtmlEditorPane
import org.jetbrains.plugins.github.ui.util.SingleValueModel
import org.jetbrains.plugins.github.util.GithubUtil.Delegates.equalVetoingObservable

internal class GHPRDescriptionPanel(private val model: SingleValueModel<out GHPullRequest?>) : NonOpaquePanel() {

  private var description: String? by equalVetoingObservable<String?>(null) {
    isVisible = !it.isNullOrEmpty()
    htmlPane.setBody(it.orEmpty())
  }

  private val htmlPane = HtmlEditorPane()

  init {
    setContent(htmlPane)

    model.addValueChangedListener {
      update()
    }
    update()
  }

  private fun update() {
    description = model.value?.bodyHTML
  }
}