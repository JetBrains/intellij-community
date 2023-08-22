// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.ui.components.dsl

import com.intellij.grazie.GrazieBundle
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.ui.CommonActionsPanel
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.ui.StatusText
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey
import java.awt.BorderLayout
import java.awt.Container
import java.awt.Insets
import java.awt.LayoutManager
import java.awt.event.ActionListener
import javax.swing.JPanel
import javax.swing.border.Border

internal fun panel(layout: LayoutManager = BorderLayout(0, 0), body: JPanel.() -> Unit) = JPanel(layout).apply(body)
internal fun Container.panel(layout: LayoutManager = BorderLayout(0, 0), constraint: Any,
                             body: JPanel.() -> Unit): JPanel = JPanel(layout).apply(body).also { add(it, constraint) }

internal fun border(@Nls text: String, hasIndent: Boolean, insets: Insets,
                    showLine: Boolean = true): Border = IdeBorderFactory.createTitledBorder(text, hasIndent, insets).setShowLine(showLine)

internal fun padding(insets: Insets): Border = IdeBorderFactory.createEmptyBorder(insets)

@Nls
internal fun msg(@PropertyKey(resourceBundle = GrazieBundle.DEFAULT_BUNDLE_NAME) key: String, vararg params: String): String {
  return GrazieBundle.message(key, *params)
}

internal fun StatusText.setEmptyTextPlaceholder(mainText: String, @Nls shortcutText: String, shortcutButton: CommonActionsPanel.Buttons,
                                                shortcutAction: () -> Unit) {
  text = mainText
  appendSecondaryText(shortcutText, SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES, ActionListener { shortcutAction() })

  val shortcut = KeymapUtil.getShortcutsText(CommonActionsPanel.getCommonShortcut(shortcutButton).shortcuts)
  appendSecondaryText(" ($shortcut)", SimpleTextAttributes.GRAYED_ATTRIBUTES, null)
}