// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.icons.inspector.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.intellij.devkit.icons.inspector.DevkitIconsInspectorBundle
import com.intellij.devkit.icons.inspector.model.IconSizes
import com.intellij.devkit.icons.inspector.model.SizeProvenance
import com.intellij.devkit.icons.inspector.model.SizeValue
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip
import kotlin.math.abs

private fun SizeValue.known() = provenance != SizeProvenance.UNAVAILABLE

private fun SizeValue.text(): String = if (width == height) "$width" else "${width}\u00D7$height"

private fun fmtFactor(f: Double): String {
  val s = String.format("%.2f", f).trimEnd('0').trimEnd('.')
  return "\u00D7$s"
}

/** `16 → 32 → 40px`: width only when square, computed values italic with a trailing "~", unavailable as "?". */
internal fun sizeTrail(sizes: IconSizes, muted: androidx.compose.ui.graphics.Color): AnnotatedString? {
  val items = listOfNotNull(sizes.source, sizes.devicePixels, sizes.painted)
  if (items.isEmpty()) return null
  return buildAnnotatedString {
    items.forEachIndexed { i, v ->
      if (i > 0) append(" \u2192 ")
      when (v.provenance) {
        SizeProvenance.MEASURED -> append(v.text())
        SizeProvenance.COMPUTED -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(v.text() + "~") }
        SizeProvenance.UNAVAILABLE -> append("?")
      }
    }
    append("px")
  }
}

/** Fixed three-row block: Source, Device pixels, Painted, with scale factors between rows that differ. */
@Composable
internal fun SizesSection(sizes: IconSizes, modifier: Modifier = Modifier) {
  val dark = JewelTheme.isDark
  val amber = amberColor(dark)
  val rows = listOf(
    "ui.sizes.source" to sizes.source,
    "ui.sizes.device" to sizes.devicePixels,
    "ui.sizes.painted" to sizes.painted,
  )
  Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
    var previous: SizeValue? = null
    for ((key, value) in rows) {
      val prev = previous
      var resized = false
      if (prev != null && value != null && prev.known() && value.known()) {
        val ratio = value.width.toDouble() / prev.width.coerceAtLeast(1)
        val differs = abs(ratio - 1.0) > 0.005 || value.height != prev.height && abs(value.height - prev.height) > 0
        // painted vs device pixels: more than 1px apart is a resize by the look/wrapper
        resized = key == "ui.sizes.painted" && (abs(value.width - prev.width) > 1 || abs(value.height - prev.height) > 1)
        if (differs) {
          val color = if (resized) amber else JewelTheme.globalColors.text.info
          Text(
            (if (resized) "\u26A0 " else "") + "\u2192 " + fmtFactor(ratio),
            color = color,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            modifier = Modifier.padding(start = 12.dp),
          )
        }
      }
      SizeRow(DevkitIconsInspectorBundle.message(key), value, if (resized) amber else null)
      if (value != null) previous = value
    }
  }
}

@Composable
private fun SizeRow(label: String, value: SizeValue?, highlight: androidx.compose.ui.graphics.Color?) {
  val info = JewelTheme.globalColors.text.info
  val row: @Composable () -> Unit = {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      Text(label, color = info, modifier = Modifier.widthIn(max = 150.dp).weight(0.4f))
      Column(Modifier.weight(0.6f)) {
        when {
          value == null -> Text(DevkitIconsInspectorBundle.message("ui.sizes.not.applicable"), color = info)
          value.provenance == SizeProvenance.UNAVAILABLE ->
            Text(DevkitIconsInspectorBundle.message("ui.sizes.unavailable") + (value.note?.let { " $it" } ?: ""), color = info)
          else -> {
            val text = buildAnnotatedString {
              append(value.width.toString() + "\u00D7" + value.height)
              if (value.provenance == SizeProvenance.COMPUTED) {
                withStyle(SpanStyle(color = info, fontFamily = FontFamily.Default, fontStyle = FontStyle.Italic)) {
                  append(" " + DevkitIconsInspectorBundle.message("ui.sizes.computed"))
                }
              }
            }
            Text(text, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = highlight ?: androidx.compose.ui.graphics.Color.Unspecified)
            if (value.note != null) Text(value.note, color = info, fontSize = 11.sp)
          }
        }
      }
    }
  }
  val note = value?.note
  if (note != null) Tooltip(tooltip = { Text(note) }) { row() } else row()
}
