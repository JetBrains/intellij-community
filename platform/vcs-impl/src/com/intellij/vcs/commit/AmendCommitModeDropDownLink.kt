// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.commit

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.UI
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.DropDownLink
import com.intellij.util.ui.launchOnShow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.Nls
import javax.swing.JList

internal class AmendCommitModeDropDownLink(val amendHandler: AmendCommitHandler) :
  DropDownLink<CommitToAmend>(CommitToAmend.Last, { link -> createPopup(link, amendHandler) }), AmendCommitModeListener, Disposable {

  init {
    amendHandler.addAmendCommitModeListener(this, this)
    foreground = JBColor.foreground()
    isRolloverEnabled = false
  }

  override fun itemToString(item: CommitToAmend): String = Companion.itemToString(item)

  override fun amendCommitModeToggled() {
    val item = if (amendHandler.commitToAmend == CommitToAmend.None) CommitToAmend.Last else amendHandler.commitToAmend

    selectedItem = item
    text = itemToLinkText(item)
  }

  override fun dispose() {
  }

  companion object {
    private const val LINK_TEXT_MAX: Int = 20
    private const val POPUP_TEXT_MAX: Int = 60
    private const val COMMITS_LIMIT: Int = 20

    private fun itemToString(item: CommitToAmend): @Nls String = when (item) {
      is CommitToAmend.None -> error("There shouldn't be a None option in the popup")
      is CommitToAmend.Last -> VcsBundle.message("dropdown.link.amend")
      is CommitToAmend.Specific -> item.targetSubject
    }

    private fun itemToText(item: CommitToAmend, maxLength: Int): @Nls String =
      StringUtil.shortenTextWithEllipsis(itemToString(item), maxLength, 0)

    private fun itemToLinkText(item: CommitToAmend): @Nls String = itemToText(item, LINK_TEXT_MAX)
    private fun itemToPopupText(item: CommitToAmend): @Nls String = itemToText(item, POPUP_TEXT_MAX)

    private fun createPopup(link: DropDownLink<CommitToAmend>, amendHandler: AmendCommitHandler): JBPopup {
      val items = mutableListOf<CommitToAmend>()
      items.add(CommitToAmend.Last)
      val builder = JBPopupFactory.getInstance()
        .createPopupChooserBuilder(items)
        .setNamerForFiltering { value ->
          itemToString(value)
        }
        .setRenderer(object : SimpleListCellRenderer<CommitToAmend>() {
          override fun customize(
            list: JList<out CommitToAmend>,
            value: CommitToAmend,
            index: Int,
            selected: Boolean,
            hasFocus: Boolean,
          ) {
            text = itemToPopupText(value)
            icon = if (value == link.selectedItem) AllIcons.Actions.Checked else AllIcons.Empty
          }
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
        val loaded = amendHandler.getAmendSpecificCommitTargets(COMMITS_LIMIT)
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
