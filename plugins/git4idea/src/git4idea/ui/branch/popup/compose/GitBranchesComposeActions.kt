// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch.popup.compose

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.onClick
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.intellij.platform.compose.onHostPreviewKeyEvent
import org.jetbrains.annotations.Nls
import org.jetbrains.jewel.foundation.lazy.SelectableLazyListScope
import org.jetbrains.jewel.foundation.lazy.SelectableLazyListState
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text

internal fun SelectableLazyListScope.branchesActions(
  actions: List<GitBranchesComposeVm.BranchesAction>,
  columnState: SelectableLazyListState,
  startingIndex: Int,
  closePopup: () -> Unit
) {
  for ((index, action) in actions.withIndex()) {
    action(
      columnState, startingIndex + index, action.title,
      icon = action.icon, iconClass = action.iconClass,
      closePopup = closePopup,
      action = {
        closePopup()
        action.action()
      }
    )
  }
}

@OptIn(ExperimentalFoundationApi::class)
private fun SelectableLazyListScope.action(
  columnState: SelectableLazyListState,
  listIndex: Int,
  title: @Nls String,
  icon: String?,
  iconClass: Class<*>?,
  action: () -> Unit,
  closePopup: () -> Unit
) {
  val key = "#Action $title"
  item(key, contentType = BranchesPopupItemType.Action) {
    BranchPopupItemBox(
      key,
      columnState,
      listIndex,
      isSelected,
      modifier = Modifier
        .onHostPreviewKeyEvent(enabled = isSelected) {
          if (it.type == KeyEventType.KeyDown && it.key == Key.Enter) {
            action()
            closePopup()
            return@onHostPreviewKeyEvent true
          }
          false
        }
        .onClick {
          action()
          closePopup()
        }
    ) {
      Row(
        modifier = Modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        if (icon != null && iconClass != null) {
          Icon(icon, null, iconClass, modifier = Modifier.requiredSize(16.dp))
        }
        else {
          Box(modifier = Modifier.requiredSize(16.dp))
        }
        Text(title)
      }
    }
  }
}