// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.ui.util

import com.intellij.collaboration.ui.HtmlEditorPaneUtil
import com.intellij.collaboration.ui.codereview.BaseHtmlEditorPane
import com.intellij.collaboration.ui.setHtmlBody
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.ui.ExtendableHTMLViewFactory
import com.intellij.util.ui.HTMLEditorKitBuilder

internal class HtmlEditorPane() : BaseHtmlEditorPane() {

  init {
    editorKit = HTMLEditorKitBuilder().withViewFactoryExtensions(
      ExtendableHTMLViewFactory.Extensions.WORD_WRAP,
      HtmlEditorPaneUtil.CONTENT_TOOLTIP,
      HtmlEditorPaneUtil.INLINE_ICON_EXTENSION,
      HtmlEditorPaneUtil.IMAGES_EXTENSION
    ).build()
  }

  constructor(@NlsSafe body: String) : this() {
    setHtmlBody(body)
  }
}