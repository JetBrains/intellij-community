// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.details

import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.util.ui.HtmlPanel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.plugins.github.util.GithubUtil.Delegates.equalVetoingObservable
import java.awt.Font

internal class GithubPullRequestDescriptionPanel : NonOpaquePanel() {
  var description: String? by equalVetoingObservable<String?>(
    null) {
    //'!it.isNullOrEmpty()' causes Kotlin compiler to fail here (KT-28847)
    isVisible = it != null && !it.isEmpty()
    htmlPanel.update()
  }

  private val htmlPanel = object : HtmlPanel() {
    init {
      border = JBUI.Borders.empty()
      editorKit = UIUtil.JBWordWrapHtmlEditorKit()
    }

    override fun update() {
      super.update()
      isVisible = !description.isNullOrEmpty()
    }

    override fun getBody() = description.orEmpty()
    override fun getBodyFont(): Font = UIUtil.getLabelFont()
  }

  init {
    setContent(htmlPanel)
  }
}