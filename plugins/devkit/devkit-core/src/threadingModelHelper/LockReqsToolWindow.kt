// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.threadingModelHelper

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import org.jetbrains.jewel.ui.component.Text


@Composable
fun LockReqsToolWindow(service: LockReqsService) {
  val results = service.getCurrentResults()
  Column() {
    Text("Found ${results.size} execution paths:")
    Column() { items(results) { path -> Text(text = path) }
    }
  }
}