// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.commit

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.UI
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.ui.SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES
import com.intellij.ui.components.DropDownLink
import com.intellij.ui.dsl.listCellRenderer.listCellRenderer
import com.intellij.util.ui.launchOnShow
import com.intellij.vcs.commit.message.CommitMessageInspectionProfile.getSubjectRightMargin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.Nls

internal class AmendCommitModeDropDownLink(val amendHandler: AmendCommitHandler) :
  DropDownLink<CommitToAmend>(CommitToAmend.Last.Unknown, { link -> createPopup(link, amendHandler) }) {
  override fun itemToString(item: CommitToAmend): String = Companion.itemToString(item)

  fun update() {
    val item = if (amendHandler.commitToAmend == CommitToAmend.None) CommitToAmend.Last.Unknown else amendHandler.commitToAmend

    selectedItem = item
    text = itemToLinkText(item)
  }

  companion object {
    private const val LINK_TEXT_MAX: Int = 20

    private fun itemToString(item: CommitToAmend): @Nls String = when (item) {
      CommitToAmend.None -> error("There shouldn't be a None option in the popup")
      is CommitToAmend.Last.Unknown -> VcsBundle.message("dropdown.link.amend")
      is CommitToAmend.Resolved -> item.subject
    }

    private fun itemToText(item: CommitToAmend, maxLength: Int): @Nls String =
      StringUtil.shortenTextWithEllipsis(itemToString(item), maxLength, 0)

    private fun itemToLinkText(item: CommitToAmend): @Nls String {
      return if (item is CommitToAmend.Last) VcsBundle.message("dropdown.link.amend") else itemToText(item, LINK_TEXT_MAX)
    }

    private fun formatLastCommitSubject(commit: CommitToAmend.Last.Known, subjectMaxLength: Int): @NlsSafe String {
      val primaryTextLength = VcsBundle.message("dropdown.link.amend").length
      val secondaryMaxLength = (subjectMaxLength - primaryTextLength - 1).coerceAtLeast(10)
      return StringUtil.shortenTextWithEllipsis(commit.subject, secondaryMaxLength, 0)
    }

    private fun createPopup(link: DropDownLink<CommitToAmend>, amendHandler: AmendCommitHandler): JBPopup {
      CommitSessionCollector.getInstance(amendHandler.project).logAmendPopupShown()

      val initialItems: List<IndexedValue<CommitToAmend>> = listOf(IndexedValue(0, CommitToAmend.Last.Unknown))
      val subjectMaxLength = getSubjectRightMargin(amendHandler.project)

      val builder = JBPopupFactory.getInstance()
        .createPopupChooserBuilder(initialItems)
        .setNamerForFiltering {
          itemToString(it.value)
        }
        .setRenderer(listCellRenderer {
          val commit = value.value
          val isSelected = commit == link.selectedItem || (commit is CommitToAmend.Last && link.selectedItem is CommitToAmend.Last)
          icon(if (isSelected) AllIcons.Actions.Checked else AllIcons.Empty)

          val lastKnown = commit as? CommitToAmend.Last.Known
          if (lastKnown != null) {
            text(VcsBundle.message("dropdown.link.amend")) {
              attributes = REGULAR_BOLD_ATTRIBUTES
            }
            val formattedSubject = formatLastCommitSubject(lastKnown, subjectMaxLength)
            text(formattedSubject) {
              speedSearch {}
              foreground = greyForeground
            }
            toolTipText = lastKnown.subject.takeIf { formattedSubject != it }
          }
          else {
            val fullText = itemToString(commit)
            val popupText = itemToText(commit, subjectMaxLength)
            text(popupText) {
              speedSearch {}
            }
            toolTipText = fullText.takeIf { popupText != it }
          }
        })
        .setVisibleRowCount(5)
        .setItemChosenCallback {
          amendHandler.commitToAmend = it.value
          CommitSessionCollector.getInstance(amendHandler.project).logAmendCommitSelected(it.index)
        }

      val popup = builder.createPopup()
      val updater = builder.backgroundUpdater

      popup.content.launchOnShow("Amend targets fetcher") {
        updater.paintBusy(true)
        val loaded = amendHandler.getAmendSpecificCommitTargets().withIndex().toList().ifEmpty { initialItems }
        updater.replaceModel(loaded)
        updater.paintBusy(false)
        withContext(Dispatchers.UI) {
          popup.pack(true, true)
        }
      }

      return popup
    }
  }
}
