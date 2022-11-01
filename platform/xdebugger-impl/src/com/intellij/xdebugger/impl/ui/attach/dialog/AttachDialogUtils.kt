package com.intellij.xdebugger.impl.ui.attach.dialog

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.ui.StartupUiUtil
import java.awt.Font
import java.awt.FontMetrics
import javax.swing.JComponent

private val logger = Logger.getInstance(AttachToProcessDialog::class.java)

fun isListMerged(): Boolean = Registry.`is`("debugger.attach.list.merged")

fun getProcessName(textToTruncate: String, fontMetrics: FontMetrics, maxWidth: Int): @NlsSafe String {
  if (textToTruncate.isEmpty()) return ""

  try {
    if (maxWidth > 0 && fontMetrics.stringWidth(textToTruncate) > maxWidth) {
      return truncateDescription(textToTruncate, fontMetrics, maxWidth)
    }
  }
  catch (e: Exception) {
    logger.error(e)
  }
  return textToTruncate
}

fun getComponentFont(component: JComponent): Font = component.font ?: StartupUiUtil.getLabelFont()

fun truncateDescription(initDescription: String, fontMetrics: FontMetrics, maxWidth: Int): String {
  if (fontMetrics.stringWidth(initDescription) <= maxWidth) return initDescription
  val ellipsisWidth = fontMetrics.charWidth('\u2026')
  return "${findBestTruncateLength(initDescription, fontMetrics, maxWidth, 0, initDescription.length - 1, ellipsisWidth)}\u2026"
}

private fun findBestTruncateLength(initDescription: String, fontMetrics: FontMetrics, maxWidth: Int, minTextBound: Int, maxEndBound: Int, ellipsisWidth: Int): String {
  if (minTextBound >= maxEndBound) {
    val truncatedDescription = initDescription.substring(0, minTextBound + 1)
    if (fontMetrics.stringWidth(truncatedDescription) + ellipsisWidth > maxWidth) {
      return initDescription.substring(0, minTextBound)
    }
    return truncatedDescription
  }
  val middle = (minTextBound + maxEndBound) / 2
  if (fontMetrics.stringWidth(initDescription.substring(0, middle + 1)) + ellipsisWidth > maxWidth) {
    return findBestTruncateLength(initDescription, fontMetrics, maxWidth, minTextBound, middle - 1, ellipsisWidth)
  }
  return findBestTruncateLength(initDescription, fontMetrics, maxWidth, middle + 1, maxEndBound, ellipsisWidth)
}
