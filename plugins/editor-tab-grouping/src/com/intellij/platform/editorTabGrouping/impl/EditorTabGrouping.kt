// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.editorTabGrouping.impl

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.JBColor
import org.jetbrains.annotations.TestOnly
import java.awt.Color

internal data class TabGroup(val name: String, val key: String)

internal const val AGENTS_GROUP_KEY = "air:agents"

private val AIR_EDITOR_TAB_CLASS_NAMES = setOf(
  "com.intellij.air.thread.view.AgentThreadViewVirtualFile",
  "com.intellij.air.frontend.agents.AgentsPageVirtualFile",
)

internal fun isAirEditorTabClass(className: String): Boolean =
  className in AIR_EDITOR_TAB_CLASS_NAMES

internal fun agentsTabGroup(): TabGroup =
  TabGroup(EditorTabGroupingBundle.message("group.agents"), AGENTS_GROUP_KEY)

@Suppress("SplitModeApiUsage")
internal object EditorTabGroupingProvider {

  private val groupColors = listOf(
    "ColorPalette.Blue4" to "ColorPalette.Blue6",
    "ColorPalette.Blue6" to "ColorPalette.Blue4",
    "ColorPalette.Purple3" to "ColorPalette.Purple7",
    "ColorPalette.Purple5" to "ColorPalette.Purple5",
    "ColorPalette.Teal3" to "ColorPalette.Teal6",
    "ColorPalette.Teal5" to "ColorPalette.Teal4",
    "ColorPalette.Green3" to "ColorPalette.Green7",
    "ColorPalette.Green5" to "ColorPalette.Green5",
    "ColorPalette.Yellow2" to "ColorPalette.Yellow7",
    "ColorPalette.Yellow4" to "ColorPalette.Yellow5",
    "ColorPalette.Orange3" to "ColorPalette.Orange7",
    "ColorPalette.Orange5" to "ColorPalette.Orange5",
  ).map { (light, dark) ->
    JBColor(JBColor.namedColor(light), JBColor.namedColor(dark))
  }

  /**
   * Returns the group of [file]: the synthetic Agents group for known AIR editor tabs,
   * otherwise the parent directory group.
   *
   * The result does not depend on a setting, so the caller can memoize it. The caller must
   * check [EditorTabGroupingSettings.groupByDirectory] itself.
   */
  fun resolveDirectoryGroup(file: VirtualFile, project: Project): TabGroup? {
    if (isAirEditorTabClass(file.javaClass.name)) {
      return agentsTabGroup()
    }
    val parent = file.parent ?: return null
    val contentRoot = ProjectFileIndex.getInstance(project).getContentRootForFile(file) ?: return null
    val relative = parent.path.removePrefix(contentRoot.path).trimStart('/')
    val key = "${contentRoot.path}/${relative}"
    return TabGroup(name = parent.name, key = key)
  }

  @TestOnly
  fun groupColors(): List<JBColor> = groupColors

  fun colorForGroup(group: TabGroup): Color {
    return groupColors[Math.floorMod(group.key.hashCode(), groupColors.size)]
  }
}
