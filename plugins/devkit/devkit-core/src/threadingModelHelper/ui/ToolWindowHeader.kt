// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.threadingModelHelper.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import org.jetbrains.idea.devkit.threadingModelHelper.LockReqsAnalyzer
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.icons.AllIconsKeys


@Composable
internal fun ToolWindowHeader(
  analysisResult: LockReqsAnalyzer.Companion.AnalysisResult?,
  searchQuery: TextFieldValue,
  onSearchQueryChange: (TextFieldValue) -> Unit
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


    if (analysisResult != null && analysisResult.paths.isNotEmpty()) {
      Box(
        modifier = Modifier
          .background(
            JewelTheme.globalColors.text.info,
            RoundedCornerShape(12.dp)
          )
          .padding(horizontal = 8.dp, vertical = 2.dp)
      ) {
        Text(text = analysisResult.paths.size.toString(), )
      }
    }

    Spacer(Modifier.weight(1f))

    // Search field
    if (analysisResult != null && analysisResult.paths.isNotEmpty()) {
      TextField(
        value = searchQuery,
        onValueChange = onSearchQueryChange,
        modifier = Modifier.width(250.dp),
        placeholder = { Text("Search paths...") },
        leadingIcon = {
          Icon(
            key = AllIconsKeys.Actions.Find,
            contentDescription = "Search",
            modifier = Modifier.size(16.dp)
          )
        },
        trailingIcon = {
          if (searchQuery.text.isNotEmpty()) {
            IconButton(
              onClick = { onSearchQueryChange(TextFieldValue("")) },
              modifier = Modifier.size(16.dp)
            ) {
              Icon(
                key = AllIconsKeys.Actions.Close,
                contentDescription = "Clear search"
              )
            }
          }
        }
      )
    }
  }
}
