// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.ui.util

import com.intellij.collaboration.ui.onHyperlinkActivated
import com.intellij.ui.BrowserHyperlinkListener
import org.jetbrains.plugins.github.pullrequest.comment.GHMarkdownToHtmlConverter
import javax.swing.JEditorPane
import javax.swing.event.HyperlinkEvent

fun JEditorPane.addGithubHyperlinkListener(openPR: (Long) -> Unit) {
  onHyperlinkActivated { e -> handleGithubHyperlink(e, openPR) }
}

fun handleGithubHyperlink(e: HyperlinkEvent, openPR: (Long) -> Unit) {
  if (e.description.startsWith(GHMarkdownToHtmlConverter.OPEN_PR_LINK_PREFIX)) {
    val prOrIssueId = e.description.removePrefix(GHMarkdownToHtmlConverter.OPEN_PR_LINK_PREFIX)
    val number = prOrIssueId.toLongOrNull() ?: return

    openPR(number)

    return
  }

  BrowserHyperlinkListener.INSTANCE.hyperlinkUpdate(e)
}