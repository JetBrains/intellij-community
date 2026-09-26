// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.threading.threadingModelHelper.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys

@Composable
internal fun NoPathsFoundView() {
  Box(
    modifier = Modifier.fillMaxSize(),
    contentAlignment = Alignment.Center
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      Icon(
        key = AllIconsKeys.General.InspectionsOK,
        contentDescription = null,
        modifier = Modifier.size(64.dp),
        tint = JewelTheme.globalColors.outlines.focused
      )
      Text(
        text = "No lock requirements found",
        style = JewelTheme.defaultTextStyle.copy(
          fontSize = JewelTheme.defaultTextStyle.fontSize * 1.5,
          fontWeight = FontWeight.Medium
        )
      )
      Text(
        text = "Method and its callees",
        color = JewelTheme.globalColors.text.info
      )
      Text(
        text = "do not require any read locks",
        color = JewelTheme.globalColors.text.info
      )
    }
  }
}