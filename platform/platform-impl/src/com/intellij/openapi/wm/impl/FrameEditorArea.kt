// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.application.impl.InternalUICustomization
import com.intellij.openapi.fileEditor.impl.EditorsSplitters
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.toolWindow.ToolWindowPane
import com.intellij.toolWindow.xNext.island.XNextIslandHolder
import com.intellij.ui.ClientProperty
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.UIUtil
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
 * A theme that paints islands paints one around the wrapper. The header and the cards share it. The editors of a card
 * paint no island of their own, see [InternalUICustomization.configureFrameEditorArea]. [ROOT_KEY] marks the wrapper and
 * [CARD_EDITORS_KEY] marks the editors of a card, so a theme switch finds them in the component tree.
 *
 * EDT only.
 */
internal class FrameEditorArea(private val pane: ToolWindowPane) {
  private var ownerEditors: JComponent? = null
  private var header: JComponent? = null

  private var root: XNextIslandHolder? = null
  private val cards = CardLayout()
  private var cardPanel: JPanel? = null
  private val guestCards = LinkedHashMap<Project, Card>()

  /** The card of a guest: [component] sits in the card stack and holds the [editors] of the guest. */
  private class Card(val component: JComponent, val editors: JComponent)

  /** Puts the editors of the frame owner into the frame. */
  @RequiresEdt
  fun setOwnerEditors(editors: JComponent) {
    ownerEditors = editors
    val cardPanel = cardPanel
    if (cardPanel == null) {
      pane.setDocumentComponent(editors)
    }
    else {
      addCard(cardPanel, editors, editors, OWNER_CARD)
    }
  }

  /** Adds the editors of [guest] as a card. The frame shows the card once [guest] is the active project. */
  @RequiresEdt
  fun addGuestEditors(guest: Project, editors: JComponent) {
    if (guest in guestCards) return
    val cardPanel = cardPanel ?: wrap()
    val card = UiDataProvider.wrapComponent(editors) { sink -> sink[CommonDataKeys.PROJECT] = guest }
    guestCards[guest] = Card(card, editors)
    addCard(cardPanel, card, editors, cardId(guest))
  }

  private fun addCard(cardPanel: JPanel, card: JComponent, editors: JComponent, id: String) {
    ClientProperty.put(editors, CARD_EDITORS_KEY, true)
    InternalUICustomization.getInstance()?.configureFrameEditorAreaCard(editors)
    cardPanel.add(card, id)
    cardPanel.revalidate()
  }

  @RequiresEdt
  fun removeGuestEditors(guest: Project) {
    val card = guestCards.remove(guest) ?: return
    val cardPanel = cardPanel ?: return
    cardPanel.remove(card.component)
    // The editors leave the island of the wrapper, so they paint their own again.
    ClientProperty.put(card.editors, CARD_EDITORS_KEY, null)
    val customization = InternalUICustomization.getInstance()
    for (splitters in UIUtil.findComponentsOfType(card.editors, EditorsSplitters::class.java)) {
      customization?.configureEditorsSplitters(splitters)
    }
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
      InternalUICustomization.getInstance()?.installEditorBackground(header)
      root.add(header, BorderLayout.NORTH)
    }
    root.revalidate()
    root.repaint()
  }

  /** Replaces the owner editors in the frame with the header slot and the card stack. */
  private fun wrap(): JPanel {
    cardPanel?.let { return it }
    val cardPanel = JPanel(cards)
    ownerEditors?.let { addCard(cardPanel, it, it, OWNER_CARD) }
    val root = XNextIslandHolder()
    root.layout = BorderLayout()
    ClientProperty.put(root, ROOT_KEY, true)
    root.add(cardPanel, BorderLayout.CENTER)
    InternalUICustomization.getInstance()?.configureFrameEditorArea(root)
    this.cardPanel = cardPanel
    this.root = root
    pane.setDocumentComponent(root)
    return cardPanel
  }

  private fun cardId(guest: Project): String = "guest:${guest.locationHash}"

  companion object {
    /** `true` on the wrapper of a frame editor area that has a header or a guest card. */
    val ROOT_KEY: Key<Boolean> = Key.create("FrameEditorArea.root")

    /** `true` on the editors of one project inside such a wrapper. */
    val CARD_EDITORS_KEY: Key<Boolean> = Key.create("FrameEditorArea.cardEditors")
  }
}

private const val OWNER_CARD = "owner"
