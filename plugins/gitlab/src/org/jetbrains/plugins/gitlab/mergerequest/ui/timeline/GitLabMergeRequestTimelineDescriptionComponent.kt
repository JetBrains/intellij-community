// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.timeline

import com.intellij.collaboration.ui.SingleValueModel
import com.intellij.collaboration.ui.codereview.CodeReviewChatItemUIUtil
import com.intellij.collaboration.ui.codereview.CodeReviewTimelineUIUtil
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.ColorUtil
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.map
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.ui.comment.GitLabNoteComponentFactory.createTextPanel
import org.jetbrains.plugins.gitlab.util.GitLabBundle
import javax.swing.JComponent

// TODO: extract common code with GitHub (see org.jetbrains.plugins.github.pullrequest.ui.timeline.GHPRTimelineItemComponentFactory)
internal object GitLabMergeRequestTimelineDescriptionComponent {
  private val noDescriptionHtmlText by lazy {
    HtmlBuilder()
      .append(GitLabBundle.message("merge.request.timeline.empty.description"))
      .wrapWith(HtmlChunk.font(ColorUtil.toHex(UIUtil.getContextHelpForeground())))
      .wrapWith("i")
      .toString()
  }

  fun createComponent(
    cs: CoroutineScope,
    vm: GitLabMergeRequestTimelineViewModel,
    avatarIconsProvider: IconsProvider<GitLabUserDTO>,
  ): JComponent {
    val titlePanel = CodeReviewTimelineUIUtil.createTitleTextPane(vm.author.name, vm.author.webUrl, date = null)
    val descriptionTextComponent = createTextPanel(cs, vm.descriptionHtml.map { it.ifBlank { noDescriptionHtmlText } }, vm.serverUrl)


    return CodeReviewChatItemUIUtil.buildDynamic(CodeReviewChatItemUIUtil.ComponentType.FULL,
                                                 { size -> SingleValueModel(avatarIconsProvider.getIcon(vm.author, size)) },
                                                 descriptionTextComponent) {
      // TODO: allow description editing
      withHeader(titlePanel, null)
    }
  }
}