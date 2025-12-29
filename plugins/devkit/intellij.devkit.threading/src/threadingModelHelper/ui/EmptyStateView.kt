// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.threading.threadingModelHelper.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys

@Composable
internal fun EmptyStateView() {
  Box(
    modifier = Modifier.fillMaxSize(),
    contentAlignment = Alignment.Center
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Icon(
        key = AllIconsKeys.Debugger.ThreadRunning,
        contentDescription = null,
        tint = JewelTheme.globalColors.text.disabled
      )
      Text(
        text = "No lock requirements analyzed yet",
        style = JewelTheme.defaultTextStyle.copy(
          fontWeight = FontWeight.Medium
        ),
        color = JewelTheme.globalColors.text.info
      )
      Text(
        text = "Place cursor on a method and run 'Analyze Lock Requirements'",
        color = JewelTheme.globalColors.text.info
      )
    }
  }
}
