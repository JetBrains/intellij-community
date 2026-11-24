// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.icons.inspector.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.intellij.devkit.icons.inspector.DevkitIconsInspectorBundle
import org.jetbrains.jewel.ui.component.Text

@Composable
internal fun IconInspectorPlaceholder() {
  Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(DevkitIconsInspectorBundle.message("inspector.empty.title"))
      Text(DevkitIconsInspectorBundle.message("inspector.empty.hint"))
    }
  }
}
