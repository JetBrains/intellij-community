// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Internal

package com.intellij.platform.vcs.impl.frontend.changes

import com.intellij.openapi.vcs.FileStatus
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.ui.JBColor.namedColor
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import javax.swing.UIManager
import kotlin.collections.get

fun findFileStatusById(id: String?): FileStatus? {
  return FILE_STATUS_MAPPING[id]
}

fun getBranchPresentationBackground(background: Color): Color = ColorUtil.mix(background, BACKGROUND_BASE_COLOR, BACKGROUND_BALANCE)

@Suppress("SameParameterValue")
private fun namedDouble(name: String, default: Double): Double {
  return when (val value = UIManager.get(name)) {
    is Double -> value
    is Int -> value.toDouble()
    is String -> value.toDoubleOrNull() ?: default
    else -> default
  }
}

private val FILE_STATUS_MAPPING: Map<String, FileStatus> = FileStatus::class.java.fields
  .mapNotNull { it.get(null) }
  .filterIsInstance<FileStatus>()
  .associateBy { it.id }

private val BACKGROUND_BALANCE
  get() = namedDouble("VersionControl.RefLabel.backgroundBrightness", 0.08)

private val BACKGROUND_BASE_COLOR = namedColor("VersionControl.RefLabel.backgroundBase", JBColor(Color.BLACK, Color.WHITE))