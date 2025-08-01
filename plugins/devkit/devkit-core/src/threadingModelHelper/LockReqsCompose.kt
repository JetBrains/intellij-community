// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.threadingModelHelper

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.intellij.openapi.components.service
import org.jetbrains.jewel.ui.component.Text
import com.intellij.openapi.project.Project

@Composable
fun LockReqsCompose(project: Project) {
  val service = remember(project) { project.service<LockReqsService>() }
  val results = service.currentResults
  Column() {
    Text("Found ${results.size} execution paths")
  }
}