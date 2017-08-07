/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.vcs.impl

import com.intellij.diff.util.Range
import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.InvokeAfterUpdateMode
import com.intellij.openapi.vcs.ex.PartialLocalLineStatusTracker
import com.intellij.openapi.vcs.ex.PartialLocalLineStatusTracker.RangeState
import com.intellij.openapi.vfs.LocalFileSystem
import org.jdom.Element

typealias TrackerState = PartialLocalLineStatusTracker.State

@State(
  name = "LineStatusTrackerManager",
  storages = arrayOf(Storage(value = StoragePathMacros.WORKSPACE_FILE))
)
class PartialLineStatusTrackerManagerState(
  private val project: Project,
  private val lineStatusTracker: LineStatusTrackerManager
) : ProjectComponent, PersistentStateComponent<Element> {
  private val NODE_PARTIAL_FILE = "file"
  private val ATT_PATH = "path"

  private val NODE_VCS = "vcs"
  private val NODE_CURRENT = "current"
  private val ATT_CONTENT = "content"

  private val NODE_RANGES = "ranges"
  private val NODE_RANGE = "range"
  private val ATT_START_1 = "start1"
  private val ATT_END_1 = "end1"
  private val ATT_START_2 = "start2"
  private val ATT_END_2 = "end2"
  private val ATT_CHANGELIST_ID = "changelist"


  override fun getState(): Element {
    val element = Element("state")
    if (Registry.`is`("vcs.enable.partial.changelists.persist")) {
      val fileStates = lineStatusTracker.collectPartiallyChangedFilesStates()
      for (state in fileStates) {
        element.addContent(writePartialFileState(state))
      }
    }

    return element
  }

  override fun loadState(element: Element) {
    if (Registry.`is`("vcs.enable.partial.changelists.persist")) {
      val fileStates = mutableListOf<TrackerState>()
      for (node in element.getChildren(NODE_PARTIAL_FILE)) {
        val state = readPartialFileState(node)
        if (state != null) fileStates.add(state)
      }

      ChangeListManager.getInstance(project).invokeAfterUpdate(
        {
          lineStatusTracker.restoreTrackersForPartiallyChangedFiles(fileStates)
        }, InvokeAfterUpdateMode.SILENT, null, null)
    }
  }

  private fun writePartialFileState(state: TrackerState): Element {
    val element = Element(NODE_PARTIAL_FILE)
    element.setAttribute(ATT_PATH, state.virtualFile.path)

    element.addContent(Element(NODE_VCS).setAttribute(ATT_CONTENT, state.vcsContent))
    element.addContent(Element(NODE_CURRENT).setAttribute(ATT_CONTENT, state.currentContent))

    val rangesNode = Element(NODE_RANGES)
    for (it in state.ranges) {
      rangesNode.addContent(writeRangeState(it))
    }
    element.addContent(rangesNode)

    return element
  }

  private fun readPartialFileState(element: Element): TrackerState? {
    val path = element.getAttributeValue(ATT_PATH) ?: return null
    val virtualFile = LocalFileSystem.getInstance().findFileByPath(path) ?: return null

    val vcsContent = element.getChild(NODE_VCS)?.getAttributeValue(ATT_CONTENT) ?: return null
    val currentContent = element.getChild(NODE_CURRENT)?.getAttributeValue(ATT_CONTENT) ?: return null

    val rangeStates = mutableListOf<RangeState>()

    val rangesNode = element.getChild(NODE_RANGES) ?: return null
    for (node in rangesNode.getChildren(NODE_RANGE)) {
      val rangeState = readRangeState(node) ?: return null
      rangeStates.add(rangeState)
    }

    return TrackerState(virtualFile, vcsContent, currentContent, rangeStates)
  }

  private fun writeRangeState(range: RangeState): Element {
    return Element(NODE_RANGE)
      .setAttribute(ATT_START_1, range.range.start1.toString())
      .setAttribute(ATT_END_1, range.range.end1.toString())
      .setAttribute(ATT_START_2, range.range.start2.toString())
      .setAttribute(ATT_END_2, range.range.end2.toString())
      .setAttribute(ATT_CHANGELIST_ID, range.changelistId)
  }

  private fun readRangeState(node: Element): RangeState? {
    val start1 = node.getAttributeValue(ATT_START_1)?.toIntOrNull() ?: return null
    val end1 = node.getAttributeValue(ATT_END_1)?.toIntOrNull() ?: return null
    val start2 = node.getAttributeValue(ATT_START_2)?.toIntOrNull() ?: return null
    val end2 = node.getAttributeValue(ATT_END_2)?.toIntOrNull() ?: return null
    val changelistId = node.getAttributeValue(ATT_CHANGELIST_ID) ?: return null
    return RangeState(Range(start1, end1, start2, end2), changelistId)
  }
}