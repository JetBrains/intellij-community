// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.threading.threadingModelHelper.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.icons.AllIconsKeys

@Composable
internal fun ToolWindowHeader(
  pathsCount: Int,
  searchQuery: TextFieldValue,
  onSearchQueryChange: (TextFieldValue) -> Unit,
  filters: List<LockTypeFilterChip> = emptyList(),
  onToggleFilter: (LockTypeFilterChip) -> Unit = {},
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(12.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    Icon(
      key = AllIconsKeys.Debugger.ThreadSuspended,
      contentDescription = "Lock Requirements",
      modifier = Modifier.size(16.dp)
    )

    if (pathsCount > 0) {
      Text(
        text = "$pathsCount paths",
        color = JewelTheme.globalColors.text.info,
        fontWeight = FontWeight.Medium
      )
    }

    // Filters
    if (filters.isNotEmpty()) {
      Row(
        modifier = Modifier.weight(1f),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        filters.forEach { chip ->
          FilterToggleChip(chip = chip, onToggle = { onToggleFilter(chip) })
        }
      }
    } else {
      Row(modifier = Modifier.weight(1f)) {}
    }

    if (pathsCount > 0) {
      TextField(
        value = searchQuery,
        onValueChange = onSearchQueryChange,
        modifier = Modifier.width(260.dp),
        placeholder = { Text("Search paths...") },
        leadingIcon = {
          Icon(
            key = AllIconsKeys.Actions.Find,
            contentDescription = "Search",
            modifier = Modifier.size(16.dp)
          )
        }
      )
    }
  }
}