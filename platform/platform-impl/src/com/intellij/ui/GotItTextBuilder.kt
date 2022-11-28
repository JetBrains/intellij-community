// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.ide.BrowserUtil
import com.intellij.lang.documentation.DocumentationMarkup
import org.jetbrains.annotations.Nls
import java.net.URL
import javax.swing.KeyStroke

interface GotItTextBuilder {
  /**
   * Adds an inline shortcut of the action with [actionId]
   */
  fun shortcut(actionId: String): String = """<shortcut actionId="$actionId"/>"""

  /**
   * Adds an inline raw shortcut. Please use only when there is no corresponding action.
   *
   * Use [KeyStroke.getKeyStroke] to create keystroke.
   *
   * For example: `KeyStroke.getKeyStroke(KeyEvent.VK_W, InputEvent.ALT_DOWN_MASK))`
   */
  fun shortcut(keyStroke: KeyStroke): String = """<shortcut raw="$keyStroke"/>"""

  /**
   * Adds an inline icon.
   *
   * [iconId] is the dot separated path for the icon from [com.intellij.icons.AllIcons].
   * For example: `AllIcons.General.Information`
   */
  fun icon(iconId: String): String = """<icon src="$iconId"/>"""

  /**
   * Adds an inline link with [text]. [action] will be executed on click.
   */
  fun link(@Nls text: String, action: () -> Unit): String

  /**
   * Adds an inline link with external arrow icon [com.intellij.icons.AllIcons.Ide.External_link_arrow]
   */
  fun browserLink(@Nls text: String, url: URL): String = link(text + DocumentationMarkup.EXTERNAL_LINK_ICON) { BrowserUtil.browse(url) }
}

internal class GotItTextBuilderImpl : GotItTextBuilder {
  private val linkActions: MutableMap<Int, () -> Unit> = mutableMapOf()
  private var curLinkId: Int = 0

  override fun link(@Nls text: String, action: () -> Unit): String {
    linkActions[curLinkId] = action
    return """<a href="${curLinkId++}">$text</a>"""
  }

  fun getLinkActions(): Map<Int, () -> Unit> = linkActions
}