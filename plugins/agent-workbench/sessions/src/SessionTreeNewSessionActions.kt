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
import com.intellij.agent.workbench.sessions.core.AgentSessionLaunchMode
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.AgentSessionProviderIconIds
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderBridge
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderBridges
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
  onCreateSession: (String, AgentSessionProvider, AgentSessionLaunchMode) -> Unit,
  popupVisible: Boolean,
  onPopupVisibleChange: (Boolean) -> Unit,
) {
  val iconSize = projectActionIconSize()
  val lastUsedBridge = remember(lastUsedProvider) { lastUsedProvider?.let(AgentSessionProviderBridges::find) }
  val canQuickCreateWithLastUsed =
    lastUsedProvider != null &&
    (lastUsedBridge == null ||
     (AgentSessionLaunchMode.STANDARD in lastUsedBridge.supportedLaunchModes && lastUsedBridge.isCliAvailable()))
  val quickCreateProvider = if (canQuickCreateWithLastUsed) lastUsedProvider else null

  Row(
    horizontalArrangement = Arrangement.spacedBy(4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (quickCreateProvider != null) {
      Tooltip(tooltip = { Text(providerDisplayName(quickCreateProvider)) }) {
        IconButton(
          onClick = { onCreateSession(path, quickCreateProvider, AgentSessionLaunchMode.STANDARD) },
          modifier = Modifier.size(projectActionSlotSize()),
        ) {
          ProviderIcon(provider = quickCreateProvider, modifier = Modifier.size(iconSize))
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
          onSelect = { provider, mode ->
            onPopupVisibleChange(false)
            onCreateSession(path, provider, mode)
          },
        )
      }
    }
  }
}

@Composable
private fun NewSessionPopup(
  onDismiss: () -> Unit,
  onSelect: (AgentSessionProvider, AgentSessionLaunchMode) -> Unit,
) {
  val providerMenuItems = rememberProviderMenuItems()
  val standardProviders = remember(providerMenuItems) {
    providerMenuItems.filter { AgentSessionLaunchMode.STANDARD in it.bridge.supportedLaunchModes }
  }
  val yoloProviders = remember(providerMenuItems) {
    providerMenuItems.filter { item ->
      AgentSessionLaunchMode.YOLO in item.bridge.supportedLaunchModes && item.bridge.yoloSessionLabelKey != null
    }
  }
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
    standardProviders.forEach { item ->
      selectableItem(
        selected = false,
        enabled = item.isCliAvailable,
        onClick = { onSelect(item.bridge.provider, AgentSessionLaunchMode.STANDARD) },
      ) {
        Row(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.padding(horizontal = 8.dp),
        ) {
          ProviderIcon(provider = item.bridge.provider, modifier = Modifier.size(14.dp))
          Text(AgentSessionsBundle.message(item.bridge.newSessionLabelKey))
        }
      }
    }

    if (yoloProviders.isNotEmpty()) {
      separator()

      passiveItem {
        Text(
          text = AgentSessionsBundle.message("toolwindow.action.new.session.section.auto"),
          color = JewelTheme.globalColors.text.info,
          modifier = Modifier.padding(horizontal = 8.dp),
        )
      }

      yoloProviders.forEach { item ->
        val yoloKey = item.bridge.yoloSessionLabelKey ?: return@forEach
        selectableItem(
          selected = false,
          enabled = item.isCliAvailable,
          onClick = { onSelect(item.bridge.provider, AgentSessionLaunchMode.YOLO) },
        ) {
          Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp),
          ) {
            ProviderIcon(provider = item.bridge.provider, modifier = Modifier.size(14.dp))
            Text(AgentSessionsBundle.message(yoloKey))
          }
        }
      }
    }
  }
}

private data class ProviderMenuItem(
  val bridge: AgentSessionProviderBridge,
  val isCliAvailable: Boolean,
)

@Composable
private fun rememberProviderMenuItems(): List<ProviderMenuItem> {
  val bridges = remember { AgentSessionProviderBridges.allBridges() }
  return remember(bridges) {
    bridges.map { bridge ->
      ProviderMenuItem(
        bridge = bridge,
        isCliAvailable = bridge.isCliAvailable(),
      )
    }
  }
}

@Composable
internal fun ProviderIcon(provider: AgentSessionProvider, modifier: Modifier = Modifier) {
  val bridge = AgentSessionProviderBridges.find(provider)
  val iconId = bridge?.iconId ?: defaultIconId(provider)
  val iconKey = iconId?.let(AgentSessionsIconKeys::byId)
  if (iconKey == null) {
    Text("?", modifier = modifier)
    return
  }
  Icon(
    key = iconKey,
    contentDescription = providerDisplayName(provider),
    modifier = modifier,
  )
}

private fun defaultIconId(provider: AgentSessionProvider): String? {
  return when (provider) {
    AgentSessionProvider.CLAUDE -> AgentSessionProviderIconIds.CLAUDE
    AgentSessionProvider.CODEX -> AgentSessionProviderIconIds.CODEX
    else -> null
  }
}

internal fun providerDisplayName(provider: AgentSessionProvider): String {
  val bridge = AgentSessionProviderBridges.find(provider)
  return if (bridge != null) AgentSessionsBundle.message(bridge.displayNameKey) else provider.value
}
