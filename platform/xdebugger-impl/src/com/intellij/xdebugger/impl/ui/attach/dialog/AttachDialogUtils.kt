// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.attach.dialog

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.ui.StartupUiUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.Font
import java.awt.FontMetrics
import javax.swing.JComponent

internal fun getProcessName(@NlsSafe textToTruncate: String, fontMetrics: FontMetrics, maxWidth: Int): @NlsSafe String {
  if (textToTruncate.isEmpty()) return ""

  try {
    if (maxWidth > 0 && fontMetrics.stringWidth(textToTruncate) > maxWidth) {
      return truncateDescription(textToTruncate, fontMetrics, maxWidth)
    }
  }
  catch (e: Exception) {
    logger<AttachToProcessDialog>().error(e)
  }
  return textToTruncate
}

internal fun getComponentFont(component: JComponent): Font = component.font ?: StartupUiUtil.labelFont

@ApiStatus.Internal
@NlsSafe
fun truncateDescription(@NlsSafe initDescription: String, fontMetrics: FontMetrics, maxWidth: Int): String {
  if (fontMetrics.stringWidth(initDescription) <= maxWidth) return initDescription
  val ellipsisWidth = fontMetrics.charWidth('\u2026')
  return "${findBestTruncateLength(initDescription, fontMetrics, maxWidth, 0, initDescription.length - 1, ellipsisWidth)}\u2026"
}

@NlsSafe
private fun findBestTruncateLength(@NlsSafe initDescription: String, fontMetrics: FontMetrics, maxWidth: Int, minTextBound: Int, maxEndBound: Int, ellipsisWidth: Int): String {
  if (minTextBound >= maxEndBound) {
    val truncatedDescription = initDescription.take(minTextBound + 1)
    if (fontMetrics.stringWidth(truncatedDescription) + ellipsisWidth > maxWidth) {
      return initDescription.take(minTextBound)
    }
    return truncatedDescription
  }
  val middle = (minTextBound + maxEndBound) / 2
  if (fontMetrics.stringWidth(initDescription.take(middle + 1)) + ellipsisWidth > maxWidth) {
    return findBestTruncateLength(initDescription, fontMetrics, maxWidth, minTextBound, middle - 1, ellipsisWidth)
  }
  return findBestTruncateLength(initDescription, fontMetrics, maxWidth, middle + 1, maxEndBound, ellipsisWidth)
}
