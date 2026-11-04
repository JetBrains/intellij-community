// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.JBColor
import com.intellij.ui.components.ActionLink
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.scale.ScaleContext
import com.intellij.ui.scale.ScaleType
import com.intellij.ui.svg.getSvgDocumentSize
import com.intellij.ui.svg.renderSvgWithSize
import com.intellij.util.ui.ImageUtil
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBImageIcon
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.Dimension
import java.awt.Font
import java.io.ByteArrayInputStream
import javax.accessibility.AccessibleContext
import javax.accessibility.AccessibleRole
import javax.swing.Action
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JTextArea
import kotlin.math.min

private const val CONTENT_WIDTH = 480
private const val LOGO_SIZE = 64
private const val MAX_BACKGROUND_HEIGHT = 160

/** Swing shows the text of a label or a button as plain text when this client property is true. */
private const val HTML_DISABLE_PROPERTY = "html.disable"

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

  private fun svgIcon(image: MigrationImageData, maxWidth: Int, maxHeight: Int): Icon {
    val data = if (JBColor.isBright()) image.light else image.dark
    val size = getSvgDocumentSize(data)
    val ratio = min(maxWidth / size.width.coerceAtLeast(1f), maxHeight / size.height.coerceAtLeast(1f))
    val width = size.width.coerceAtLeast(1f) * ratio
    val height = size.height.coerceAtLeast(1f) * ratio
    val context = ScaleContext.create()
    val rendered = renderSvgWithSize(ByteArrayInputStream(data), width, height, context.getScale(ScaleType.SYS_SCALE).toFloat())
    return JBImageIcon(ImageUtil.ensureHiDPI(rendered, context, width.toDouble(), height.toDouble()))
  }
}

/**
 * A wrapped text that Swing never renders as HTML. The migration dialog shows server texts with it.
 */
@ApiStatus.Internal
fun plainTextArea(text: @NlsSafe String, font: Font, width: Int): JTextArea {
  return PlainTextArea(text).apply {
    isEditable = false
    isFocusable = false
    isOpaque = false
    lineWrap = true
    wrapStyleWord = true
    border = JBUI.Borders.empty()
    this.font = font
    foreground = UIUtil.getLabelForeground()
    // a wrapped text area computes its height from its current width
    setSize(width, Short.MAX_VALUE.toInt())
    preferredSize = Dimension(width, preferredSize.height)
  }
}

/** A read-only text. A screen reader announces it as a label, not as a text field. */
private class PlainTextArea(text: @NlsSafe String) : JTextArea(text) {
  override fun getAccessibleContext(): AccessibleContext {
    if (accessibleContext == null) {
      accessibleContext = object : AccessibleJTextArea() {
        override fun getAccessibleRole(): AccessibleRole = AccessibleRole.LABEL
      }
    }
    return accessibleContext
  }
}

/**
 * A button whose text Swing never renders as HTML.
 */
@ApiStatus.Internal
fun plainTextButton(text: @NlsSafe String, action: () -> Unit): JButton {
  return JButton().apply {
    putClientProperty(HTML_DISABLE_PROPERTY, true)
    this.text = text
    addActionListener { action() }
  }
}

/**
 * A link whose text Swing never renders as HTML.
 */
@ApiStatus.Internal
fun plainTextLink(text: @NlsSafe String, action: () -> Unit): ActionLink {
  return ActionLink().apply {
    putClientProperty(HTML_DISABLE_PROPERTY, true)
    this.text = text
    setExternalLinkIcon()
    addActionListener { action() }
  }
}
