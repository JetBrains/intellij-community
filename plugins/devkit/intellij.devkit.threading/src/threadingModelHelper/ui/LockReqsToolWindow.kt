// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.threading.threadingModelHelper.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.intellij.devkit.threading.threadingModelHelper.LockReqsService
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.jetbrains.idea.devkit.threadingModelHelper.AnalysisResult
import org.jetbrains.idea.devkit.threadingModelHelper.ConstraintType

@Composable
internal fun LockReqsToolWindow(project: Project) {
  val service = remember(project) { project.service<LockReqsService>() }
  val analysisResult: AnalysisResult? = service.currentResults.firstOrNull()
  var state by remember { mutableStateOf(LockReqsViewState()) }

  val paths = analysisResult?.paths?.toList() ?: emptyList()
  state = state.copy(allPaths = paths, selected = state.selected?.takeIf { it in paths })

  val chips = ConstraintType.entries.map { type -> LockTypeFilterChip(type, type in state.selectedTypes) }

  Column(
    modifier = Modifier.fillMaxSize()
  ) {

    ToolWindowHeader(
      pathsCount = state.filteredPaths.size,
      searchQuery = TextFieldValue(state.query),
      onSearchQueryChange = { newValue -> state = state.copy(query = newValue.text) },
      filters = chips,
      onToggleFilter = { chip ->
        val next = state.selectedTypes.toMutableSet().apply {
          if (chip.selected) remove(chip.type) else add(chip.type)
        }
        state = state.copy(selectedTypes = next)
      }
    )

    if (analysisResult == null) {
      EmptyStateView()
      return
    }

    if (state.filteredPaths.isEmpty()) {
      NoPathsFoundView()
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
          paths = state.filteredPaths,
          searchQuery = TextFieldValue(state.query),
          selectedPath = state.selected,
          onPathSelected = { state = state.copy(selected = it) }
        )
      }

      Column(modifier = Modifier.weight(1.2f)) {
        PathDetailsView(
          project = project,
          selectedPath = state.selected
        )
      }
    }
  }
}