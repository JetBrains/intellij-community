// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.intellij.agent.workbench.prompt.context.AgentPromptContextKinds
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextItem
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderBridge
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.FileUtil
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.SystemProperties
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.Nls
import java.awt.Component
import java.nio.file.FileSystems
import javax.swing.Icon
import javax.swing.JList
import javax.swing.ListCellRenderer

internal data class ProviderEntry(
  val bridge: AgentSessionProviderBridge,
  val displayName: @Nls String,
  val isCliAvailable: Boolean,
  val icon: Icon,
)

internal data class ContextEntry(
  val item: AgentPromptContextItem,
  val projectBasePath: String? = null,
  val id: String = item.kindId + ":" + item.title + ":" + item.content.hashCode(),
) {
  val displayText: String = run {
    val firstLine = item.content.lineSequence().firstOrNull()?.trim().orEmpty()
    val firstLinePreview = firstLine.toShortContextChipPathIfNeeded(
      kindId = item.kindId,
      projectBasePath = projectBasePath,
    )
    if (firstLinePreview.isEmpty()) {
      item.title
    }
    else {
      val preview = if (firstLinePreview.length <= 60) firstLinePreview else firstLinePreview.take(60) + "\u2026"
      "${item.title}: $preview"
    }
  }

  val tooltipText: String? = item.metadata
    .takeIf { it.isNotEmpty() }
    ?.entries
    ?.joinToString(separator = ", ") { (key, value) -> "$key=$value" }
}

private fun String.toShortContextChipPathIfNeeded(kindId: String, projectBasePath: String?): String {
  return when (kindId) {
    AgentPromptContextKinds.FILE -> shortenPathForChip(projectBasePath)
    AgentPromptContextKinds.PATHS -> shortenPrefixedPathForChip(projectBasePath)
    else -> this
  }
}

private fun String.shortenPrefixedPathForChip(projectBasePath: String?): String {
  val prefixes = arrayOf("file: ", "dir: ")
  val prefix = prefixes.firstOrNull { startsWith(it) } ?: return this
  val pathPart = removePrefix(prefix)
  val shortenedPath = pathPart.shortenPathForChip(projectBasePath)
  return prefix + shortenedPath
}

private fun String.shortenPathForChip(projectBasePath: String?): String {
  if (!FileUtil.isAbsolute(this)) {
    return this
  }

  val path = FileUtil.toSystemDependentName(this)
  val projectPath = projectBasePath
    ?.takeIf { it.isNotBlank() }
    ?.let(FileUtil::toSystemDependentName)
  if (!projectPath.isNullOrBlank() && FileUtil.isAncestor(projectPath, path, false)) {
    val relative = FileUtil.getRelativePath(projectPath, path, FileSystems.getDefault().separator[0])
    return if (relative.isNullOrEmpty()) "." else relative
  }

  val userHome = FileUtil.toSystemDependentName(SystemProperties.getUserHome())
  if (FileUtil.isAncestor(userHome, path, false)) {
    return if (FileUtil.pathsEqual(userHome, path)) {
      "~"
    }
    else {
      FileUtil.getLocationRelativeToUserHome(path, false)
    }
  }

  return path
}

internal data class ThreadEntry(
  val id: String,
  val displayText: @NlsSafe String,
  val secondaryText: @NlsSafe String,
)

internal class ExistingTaskCellRenderer : ListCellRenderer<ThreadEntry> {
  override fun getListCellRendererComponent(
    list: JList<out ThreadEntry>,
    value: ThreadEntry?,
    index: Int,
    isSelected: Boolean,
    cellHasFocus: Boolean,
  ): Component {
    val component = SimpleColoredComponent()
    if (value != null) {
      component.append(value.displayText, SimpleTextAttributes.REGULAR_ATTRIBUTES)
      component.append(value.secondaryText, SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
    }
    component.border = JBUI.Borders.empty(3, 4)
    component.isOpaque = true
    component.background = if (isSelected) list.selectionBackground else list.background
    component.foreground = if (isSelected) list.selectionForeground else list.foreground
    return component
  }
}
