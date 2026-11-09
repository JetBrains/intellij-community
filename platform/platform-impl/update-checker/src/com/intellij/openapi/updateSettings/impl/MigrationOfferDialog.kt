// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI
import com.intellij.openapi.application.MigrationImageData
import com.intellij.openapi.application.migrationSvgIcon
import com.intellij.openapi.application.plainTextArea
import com.intellij.openapi.application.plainTextButton
import com.intellij.openapi.application.plainTextLink
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.JBColor
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import javax.swing.Action
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JComponent

private const val CONTENT_WIDTH = 480
private const val LOGO_SIZE = 64
private const val MAX_BACKGROUND_HEIGHT = 160

/**
 * The offer of the migration to another product. The server gives all texts and images. The client defines the actions.
 */
internal class MigrationOfferDialog(
  private val project: Project?,
  private val content: MigrationOfferContent,
) : DialogWrapper(project) {
  private val offer = content.offer
  private val primaryButton = plainTextButton(offer.primaryButton) {
    close(OK_EXIT_CODE)
    startMigrationUpdate(project, content)
  }

  init {
    title = offer.title
    init()
    window.accessibleContext.accessibleDescription = offer.body
  }

  override fun getPreferredFocusedComponent(): JComponent = primaryButton

  override fun createActions(): Array<Action> = emptyArray()

  override fun createCenterPanel(): JComponent {
    val width = JBUI.scale(CONTENT_WIDTH)
    val logoSize = JBUI.scale(LOGO_SIZE)
    return panel {
      content.background?.let { background ->
        row { icon(svgIcon(background, width, JBUI.scale(MAX_BACKGROUND_HEIGHT))) }
      }
      row {
        icon(svgIcon(content.sourceLogo, logoSize, logoSize))
        icon(AllIcons.General.ArrowRight)
        icon(svgIcon(content.targetLogo, logoSize, logoSize))
      }
      row { cell(plainTextArea(offer.title, JBFont.h2().asBold(), width)) }
      row { cell(plainTextArea(offer.body, JBFont.label(), width)) }
      for (bullet in offer.bullets) {
        row { cell(plainTextArea("• ${bullet}", JBFont.label(), width)) }
      }
      offer.learnMore?.let { link ->
        row { cell(plainTextLink(link.text) { BrowserUtil.browse(link.url, project) }) }
      }
    }
  }

  override fun createSouthPanel(): JComponent {
    primaryButton.putClientProperty(DarculaButtonUI.DEFAULT_STYLE_KEY, true)
    rootPane.defaultButton = primaryButton
    val secondary = plainTextButton(offer.secondaryButton) { doCancelAction() }
    return panel {
      row {
        cell(secondary).align(AlignX.RIGHT).resizableColumn()
        cell(primaryButton)
      }
    }
  }

  /** "Remind Me Later", Esc and the close button. */
  override fun doCancelAction() {
    IdeUpdateWidgetState.getInstance().remindMeLater()
    super.doCancelAction()
  }

  private fun svgIcon(image: MigrationImageData, maxWidth: Int, maxHeight: Int): Icon =
    migrationSvgIcon(image, maxWidth, maxHeight, isDark = !JBColor.isBright())
}
