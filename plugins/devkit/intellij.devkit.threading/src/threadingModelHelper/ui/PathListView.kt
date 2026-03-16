// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.threading.threadingModelHelper.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import org.jetbrains.idea.devkit.threadingModelHelper.ExecutionPath
import org.jetbrains.jewel.foundation.lazy.SelectableLazyColumn
import org.jetbrains.jewel.foundation.lazy.SelectionMode
import org.jetbrains.jewel.foundation.lazy.items
import org.jetbrains.jewel.foundation.lazy.rememberSelectableLazyListState
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer

@Composable
internal fun PathListView(
  paths: List<ExecutionPath>,
  searchQuery: TextFieldValue,
  selectedPath: ExecutionPath?,
  onPathSelected: (ExecutionPath?) -> Unit,
) {
  val filteredPaths = remember(paths, searchQuery.text) {
    val query = searchQuery.text
    if (query.isEmpty()) paths
    else paths.filter { path ->
      val chain = path.methodChain.joinToString(" -> ") {
        "${it.containingClassName}.${it.methodName}"
      }
      chain.contains(query, ignoreCase = true)
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
        onPathSelected(indices.firstOrNull()?.let { filteredPaths[it] })
      }
    ) {
      items(
        items = filteredPaths,
        key = { path ->
          val chain = path.methodChain.joinToString("->") { it.methodName }
          "${chain}|${path.lockRequirement.constraintType}|${path.lockRequirement.requirementReason}|${path.isSpeculative}|${path.hashCode()}"
        }
      ) { path ->
        PathListItem(
          path = path,
          isSelected = path == selectedPath,
          modifier = Modifier.fillMaxWidth()
        )
      }
    }
  }
}