// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.util

import com.intellij.collaboration.ui.onHyperlinkActivated
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.ui.BrowserHyperlinkListener
import org.jetbrains.annotations.ApiStatus
import javax.swing.JEditorPane

/**
 * The links that the backend writes into a rendered note: to a file in the repository, or to another merge request.
 */
@ApiStatus.Internal
object GitLabNoteLinks {
  const val OPEN_FILE_LINK_PREFIX: String = "glfilelink:"
  const val OPEN_MR_LINK_PREFIX: String = "glmergerequest:"

  fun isGitLabLink(link: String): Boolean = link.startsWith(OPEN_FILE_LINK_PREFIX) || link.startsWith(OPEN_MR_LINK_PREFIX)

  /**
   * Opens the links of [GitLabNoteLinks] in [pane] with [linkHandler] and the other links in the browser.
   */
  fun addHyperlinkListener(pane: JEditorPane, linkHandler: GitLabNoteLinkHandler) {
    pane.onHyperlinkActivated { e ->
      if (isGitLabLink(e.description)) linkHandler.openGitLabLink(e.description)
      else BrowserHyperlinkListener.INSTANCE.hyperlinkUpdate(e)
    }
  }
}

/**
 * Opens a link of [GitLabNoteLinks]. Opening one needs the repository and the merge requests of the backend.
 *
 * The backend registers the handler of the project.
 */
@ApiStatus.Internal
fun interface GitLabNoteLinkHandler {
  fun openGitLabLink(link: String)

  companion object {
    fun getInstance(project: Project): GitLabNoteLinkHandler = project.service()
  }
}
