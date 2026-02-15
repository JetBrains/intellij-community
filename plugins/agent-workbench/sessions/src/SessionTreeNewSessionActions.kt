// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

// @spec community/plugins/agent-workbench/spec/actions/new-thread.spec.md

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.modifier.onHover
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.PopupMenu
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.component.separator
import org.jetbrains.jewel.ui.icons.AllIconsKeys

@OptIn(ExperimentalJewelApi::class, ExperimentalFoundationApi::class)
@Composable
internal fun NewSessionHoverActions(
  path: String,
  lastUsedProvider: AgentSessionProvider?,
  onCreateSession: (String, AgentSessionProvider, Boolean) -> Unit,
  popupVisible: Boolean,
  onPopupVisibleChange: (Boolean) -> Unit,
) {
  val iconSize = projectActionIconSize()

  Row(
    horizontalArrangement = Arrangement.spacedBy(4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (lastUsedProvider != null) {
      Tooltip(tooltip = { Text(providerDisplayName(lastUsedProvider)) }) {
        IconButton(
          onClick = { onCreateSession(path, lastUsedProvider, false) },
          modifier = Modifier.size(projectActionSlotSize()),
        ) {
          ProviderIcon(provider = lastUsedProvider, modifier = Modifier.size(iconSize))
        }
      }
    }

    Box {
      IconButton(
        onClick = { onPopupVisibleChange(true) },
        modifier = Modifier.size(projectActionSlotSize()),
      ) {
        Icon(
          key = AllIconsKeys.General.Add,
          contentDescription = AgentSessionsBundle.message("toolwindow.action.new.session.tooltip"),
          modifier = Modifier.size(iconSize),
        )
      }

      if (popupVisible) {
        NewSessionPopup(
          onDismiss = { onPopupVisibleChange(false) },
          onSelect = { provider, yolo ->
            onPopupVisibleChange(false)
            onCreateSession(path, provider, yolo)
          },
        )
      }
    }
  }
}

@Composable
private fun NewSessionPopup(
  onDismiss: () -> Unit,
  onSelect: (AgentSessionProvider, Boolean) -> Unit,
) {
  val claudeAvailable = remember { isAgentCliAvailable(AgentSessionProvider.CLAUDE) }
  val codexAvailable = remember { isAgentCliAvailable(AgentSessionProvider.CODEX) }
  var isHovered by remember { mutableStateOf(false) }

  PopupMenu(
    onDismissRequest = {
      if (it == InputMode.Touch && !isHovered) {
        onDismiss()
        true
      }
      else {
        onDismiss()
        true
      }
    },
    horizontalAlignment = Alignment.Start,
    modifier = Modifier.onHover { isHovered = it },
  ) {
    selectableItem(
      selected = false,
      enabled = claudeAvailable,
      onClick = { onSelect(AgentSessionProvider.CLAUDE, false) },
    ) {
      Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 8.dp),
      ) {
        ProviderIcon(provider = AgentSessionProvider.CLAUDE, modifier = Modifier.size(14.dp))
        Text(AgentSessionsBundle.message("toolwindow.action.new.session.claude"))
      }
    }
    selectableItem(
      selected = false,
      enabled = codexAvailable,
      onClick = { onSelect(AgentSessionProvider.CODEX, false) },
    ) {
      Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 8.dp),
      ) {
        ProviderIcon(provider = AgentSessionProvider.CODEX, modifier = Modifier.size(14.dp))
        Text(AgentSessionsBundle.message("toolwindow.action.new.session.codex"))
      }
    }

    separator()

    passiveItem {
      Text(
        text = AgentSessionsBundle.message("toolwindow.action.new.session.section.auto"),
        color = JewelTheme.globalColors.text.info,
        modifier = Modifier.padding(horizontal = 8.dp),
      )
    }
    selectableItem(
      selected = false,
      enabled = claudeAvailable,
      onClick = { onSelect(AgentSessionProvider.CLAUDE, true) },
    ) {
      Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 8.dp),
      ) {
        ProviderIcon(provider = AgentSessionProvider.CLAUDE, modifier = Modifier.size(14.dp))
        Text(AgentSessionsBundle.message("toolwindow.action.new.session.claude.yolo"))
      }
    }
    selectableItem(
      selected = false,
      enabled = codexAvailable,
      onClick = { onSelect(AgentSessionProvider.CODEX, true) },
    ) {
      Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 8.dp),
      ) {
        ProviderIcon(provider = AgentSessionProvider.CODEX, modifier = Modifier.size(14.dp))
        Text(AgentSessionsBundle.message("toolwindow.action.new.session.codex.yolo"))
      }
    }
  }
}

@Composable
internal fun ProviderIcon(provider: AgentSessionProvider, modifier: Modifier = Modifier) {
  val iconKey = when (provider) {
    AgentSessionProvider.CLAUDE -> AgentSessionsIconKeys.Claude
    AgentSessionProvider.CODEX -> AgentSessionsIconKeys.Codex
  }
  Icon(
    key = iconKey,
    contentDescription = providerDisplayName(provider),
    modifier = modifier,
  )
}

private fun providerDisplayName(provider: AgentSessionProvider): String =
  when (provider) {
    AgentSessionProvider.CLAUDE -> AgentSessionsBundle.message("toolwindow.provider.claude")
    AgentSessionProvider.CODEX -> AgentSessionsBundle.message("toolwindow.provider.codex")
  }
