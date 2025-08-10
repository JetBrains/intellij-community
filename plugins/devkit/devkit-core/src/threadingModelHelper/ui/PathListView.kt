// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.threadingModelHelper.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import org.jetbrains.idea.devkit.threadingModelHelper.LockReqsAnalyzer
import org.jetbrains.jewel.foundation.lazy.SelectableLazyColumn
import org.jetbrains.jewel.foundation.lazy.SelectionMode
import org.jetbrains.jewel.foundation.lazy.items
import org.jetbrains.jewel.foundation.lazy.rememberSelectableLazyListState
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer

@Composable
internal fun PathListView(
  paths: List<LockReqsAnalyzer.Companion.ExecutionPath>,
  searchQuery: TextFieldValue,
  selectedPath: LockReqsAnalyzer.Companion.ExecutionPath?,
  onPathSelected: (LockReqsAnalyzer.Companion.ExecutionPath?) -> Unit,
) {
  val filteredPaths = remember(paths, searchQuery.text) {
    paths.filter { path ->
      searchQuery.text.isEmpty() || path.pathString.contains(searchQuery.text, ignoreCase = true)
    }
  }

  val listState = rememberSelectableLazyListState()

  VerticallyScrollableContainer(
    scrollState = listState.lazyListState,
    modifier = Modifier.fillMaxSize()
  ) {
    SelectableLazyColumn(
      modifier = Modifier.fillMaxSize(),
      state = listState,
      selectionMode = SelectionMode.Single,
      onSelectedIndexesChange = { indices ->
        onPathSelected(
          if (indices.isNotEmpty()) filteredPaths[indices.first()]
          else null
        )
      }
    ) {
      items(
        items = filteredPaths,
        key = { it.pathString }
      ) { path ->
        PathListItem(
          path = path,
          isSelected = isSelected,
          isActive = isActive,
          modifier = Modifier.fillMaxWidth()
        )
      }
    }
  }
}