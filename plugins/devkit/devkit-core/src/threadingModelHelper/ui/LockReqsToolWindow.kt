// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.threadingModelHelper.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.jetbrains.idea.devkit.threadingModelHelper.LockReqsAnalyzer
import org.jetbrains.idea.devkit.threadingModelHelper.LockReqsService
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.HorizontalSplitLayout
import org.jetbrains.jewel.ui.component.rememberSplitLayoutState


@Composable
fun LockReqsToolWindow(project: Project) {
  val service = remember(project) { project.service<LockReqsService>() }
  var analysisResult by remember { mutableStateOf(service.currentResult) }
  var selectedPath by remember { mutableStateOf<LockReqsAnalyzer.Companion.ExecutionPath?>(null) }
  var searchQuery by remember { mutableStateOf(TextFieldValue("")) }

  DisposableEffect(service) {
    val listener: (LockReqsAnalyzer.Companion.AnalysisResult?) -> Unit = { result ->
      analysisResult = result
      selectedPath = null
    }
    service.onResultsUpdated = listener
    onDispose {
      service.onResultsUpdated = null
    }
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(JewelTheme.globalColors.panelBackground)
  ) {
    ToolWindowHeader(
      analysisResult = analysisResult,
      searchQuery = searchQuery,
      onSearchQueryChange = { searchQuery = it }
    )

    Divider(orientation = Orientation.Horizontal)

    when {
      analysisResult == null -> EmptyStateView()
      analysisResult!!.paths.isEmpty() -> NoPathsFoundView(analysisResult!!.method)
      else -> {
        HorizontalSplitLayout(
          first = {
            PathListView(
              paths = analysisResult!!.paths,
              searchQuery = searchQuery,
              selectedPath = selectedPath,
              onPathSelected = { selectedPath = it }
            )
          },
          second = {
            PathDetailsView(
              project = project,
              selectedPath = selectedPath
            )
          },
        )
      }
    }
  }
}





