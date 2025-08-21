// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.threadingModelHelper.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.jetbrains.idea.devkit.threadingModelHelper.AnalysisResult
import org.jetbrains.idea.devkit.threadingModelHelper.ExecutionPath
import org.jetbrains.idea.devkit.threadingModelHelper.LockReqsService

@Composable
internal fun LockReqsToolWindow(project: Project) {
  val service = remember(project) { project.service<LockReqsService>() }

  val analysisResult: AnalysisResult? = service.currentResult

  var searchQuery by rememberSaveable { mutableStateOf(TextFieldValue("")) }
  var selectedPath by remember { mutableStateOf<ExecutionPath?>(null) }

  Column(
    modifier = Modifier.fillMaxSize()
  ) {
    val paths = analysisResult?.paths?.toList() ?: emptyList()

    ToolWindowHeader(
      pathsCount = paths.size,
      searchQuery = searchQuery,
      onSearchQueryChange = { newValue -> searchQuery = newValue }
    )

    if (analysisResult == null) {
      EmptyStateView()
      return
    }

    if (paths.isEmpty()) {
      NoPathsFoundView(analysisResult.method)
      return
    }

    Row(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 8.dp, vertical = 8.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Column(modifier = Modifier.weight(1f)) {
        PathListView(
          paths = paths,
          searchQuery = searchQuery,
          selectedPath = selectedPath,
          onPathSelected = { selectedPath = it }
        )
      }

      Column(modifier = Modifier.weight(1.2f)) {
        PathDetailsView(
          project = project,
          selectedPath = selectedPath
        )
      }
    }
  }
}