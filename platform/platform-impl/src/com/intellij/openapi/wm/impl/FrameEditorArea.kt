// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.project.Project
import com.intellij.toolWindow.ToolWindowPane
import com.intellij.util.concurrency.annotations.RequiresEdt
import java.awt.BorderLayout
import java.awt.CardLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * The editor area of a project frame: the editors of the frame owner, and one card per frame guest.
 *
 * A plain frame puts the editors of its owner into the tool-window pane directly. The first guest, or a header, wraps them
 * once: a panel with the header at the top and a card stack below it, the owner editors as the first card and one card per
 * guest. The frame shows the card of the active project, see `WindowManagerEx.setActiveProject`.
 *
 * The empty part of a guest card gives the guest as the project of the data context. The tabs and the editors of the guest
 * give it on their own.
 *
 * EDT only.
 */
internal class FrameEditorArea(private val pane: ToolWindowPane) {
  private var ownerEditors: JComponent? = null
  private var header: JComponent? = null

  private var root: JPanel? = null
  private val cards = CardLayout()
  private var cardPanel: JPanel? = null
  private val guestCards = LinkedHashMap<Project, JComponent>()

  /** Puts the editors of the frame owner into the frame. */
  @RequiresEdt
  fun setOwnerEditors(editors: JComponent) {
    ownerEditors = editors
    val cardPanel = cardPanel
    if (cardPanel == null) {
      pane.setDocumentComponent(editors)
    }
    else {
      cardPanel.add(editors, OWNER_CARD)
      cardPanel.revalidate()
    }
  }

  /** Adds the editors of [guest] as a card. The frame shows the card once [guest] is the active project. */
  @RequiresEdt
  fun addGuestEditors(guest: Project, editors: JComponent) {
    if (guest in guestCards) return
    val cardPanel = cardPanel ?: wrap()
    val card = UiDataProvider.wrapComponent(editors) { sink -> sink[CommonDataKeys.PROJECT] = guest }
    guestCards[guest] = card
    cardPanel.add(card, cardId(guest))
    cardPanel.revalidate()
  }

  @RequiresEdt
  fun removeGuestEditors(guest: Project) {
    val card = guestCards.remove(guest) ?: return
    val cardPanel = cardPanel ?: return
    cardPanel.remove(card)
    cardPanel.revalidate()
    cardPanel.repaint()
  }

  /** Shows the editors of the frame owner. Nothing happens without a card stack: the owner editors are the only ones. */
  @RequiresEdt
  fun showOwnerEditors() {
    val cardPanel = cardPanel ?: return
    cards.show(cardPanel, OWNER_CARD)
  }

  /** Shows the card of [guest]. A guest without a card, one that is still opening, changes nothing. */
  @RequiresEdt
  fun showGuestEditors(guest: Project) {
    if (guest !in guestCards) return
    val cardPanel = cardPanel ?: return
    cards.show(cardPanel, cardId(guest))
  }

  /** Puts [header] above the editors, or takes the header away with `null`. A plain frame without a header stays as it is. */
  @RequiresEdt
  fun setHeader(header: JComponent?) {
    if (cardPanel == null) {
      if (header == null) return
      wrap()
    }
    val root = root ?: return
    this.header?.let { root.remove(it) }
    this.header = header
    if (header != null) {
      root.add(header, BorderLayout.NORTH)
    }
    root.revalidate()
    root.repaint()
  }

  /** Replaces the owner editors in the frame with the header slot and the card stack. */
  private fun wrap(): JPanel {
    cardPanel?.let { return it }
    val cardPanel = JPanel(cards)
    ownerEditors?.let { cardPanel.add(it, OWNER_CARD) }
    val root = JPanel(BorderLayout())
    root.add(cardPanel, BorderLayout.CENTER)
    this.cardPanel = cardPanel
    this.root = root
    pane.setDocumentComponent(root)
    return cardPanel
  }

  private fun cardId(guest: Project): String = "guest:${guest.locationHash}"
}

private const val OWNER_CARD = "owner"
