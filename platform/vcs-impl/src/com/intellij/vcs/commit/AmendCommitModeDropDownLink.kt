// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.commit

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.UI
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.ui.components.DropDownLink
import com.intellij.ui.dsl.listCellRenderer.listCellRenderer
import com.intellij.util.ui.launchOnShow
import com.intellij.vcs.commit.message.CommitMessageInspectionProfile.getSubjectRightMargin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.Nls

internal class AmendCommitModeDropDownLink(val amendHandler: AmendCommitHandler) :
  DropDownLink<CommitToAmend>(CommitToAmend.Last, { link -> createPopup(link, amendHandler) }) {
  override fun itemToString(item: CommitToAmend): String = Companion.itemToString(item)

  fun update() {
    val item = if (amendHandler.commitToAmend == CommitToAmend.None) CommitToAmend.Last else amendHandler.commitToAmend

    selectedItem = item
    text = itemToLinkText(item)
  }

  companion object {
    private const val LINK_TEXT_MAX: Int = 20

    private fun itemToString(item: CommitToAmend): @Nls String = when (item) {
      is CommitToAmend.None -> error("There shouldn't be a None option in the popup")
      is CommitToAmend.Last -> VcsBundle.message("dropdown.link.amend")
      is CommitToAmend.Specific -> item.targetSubject
    }

    private fun itemToText(item: CommitToAmend, maxLength: Int): @Nls String =
      StringUtil.shortenTextWithEllipsis(itemToString(item), maxLength, 0)

    private fun itemToLinkText(item: CommitToAmend): @Nls String = itemToText(item, LINK_TEXT_MAX)

    private fun createPopup(link: DropDownLink<CommitToAmend>, amendHandler: AmendCommitHandler): JBPopup {
      val items = mutableListOf<CommitToAmend>()
      items.add(CommitToAmend.Last)
      val subjectMaxLength = getSubjectRightMargin(amendHandler.project)
      val builder = JBPopupFactory.getInstance()
        .createPopupChooserBuilder(items)
        .setNamerForFiltering { value ->
          itemToString(value)
        }
        .setRenderer(listCellRenderer {
          val fullText = itemToString(value)
          val popupText = itemToText(value, subjectMaxLength)
          icon(if (value == link.selectedItem) AllIcons.Actions.Checked else AllIcons.Empty)
          text(popupText) {
            speedSearch {}
          }
          toolTipText = if (popupText == fullText) null else fullText
        })
        .setSelectedValue(link.selectedItem, true)
        .setVisibleRowCount(5)
        .setItemChosenCallback {
          amendHandler.commitToAmend = it
        }

      val popup = builder.createPopup()
      val updater = builder.backgroundUpdater

      popup.content.launchOnShow("Amend targets fetcher") {
        updater.paintBusy(true)
        val loaded = amendHandler.getAmendSpecificCommitTargets()
        val newModel = listOf(CommitToAmend.Last) + loaded
        updater.replaceModel(newModel)
        updater.paintBusy(false)
        withContext(Dispatchers.UI) {
          popup.pack(true, true)
        }
      }

      return popup
    }
  }
}
