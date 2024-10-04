// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.impl

import com.intellij.diff.util.Range
import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.ex.ChangelistsLocalLineStatusTracker
import com.intellij.openapi.vcs.ex.ChangelistsLocalLineStatusTracker.RangeState
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.xml.util.XmlStringUtil
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls

private typealias TrackerState = ChangelistsLocalLineStatusTracker.State
private typealias FullTrackerState = ChangelistsLocalLineStatusTracker.FullState

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
@State(name = "LineStatusTrackerManager", storages = [(Storage(value = StoragePathMacros.WORKSPACE_FILE))])
@NonNls
class PartialLineStatusTrackerManagerState(private val project: Project) : PersistentStateComponent<Element> {
  private var storedFileStates: List<TrackerState> = listOf()
  private var wasLoaded: Boolean = false

  override fun getState(): Element {
    val element = Element("state")

    val areChangeListsEnabled = ChangeListManager.getInstance(project).areChangeListsEnabled()
    val fileStates = when {
      areChangeListsEnabled -> LineStatusTrackerManager.getInstanceImpl(project).collectPartiallyChangedFilesStates()
      storedFileStates.size < DISABLED_FILES_THRESHOLD -> storedFileStates
      else -> emptyList()
    }
    for (state in fileStates) {
      element.addContent(writePartialFileState(state))
    }

    return element
  }

  override fun loadState(element: Element) {
    if (wasLoaded) return
    storedFileStates = parseStates(element)
  }

  private fun getStatesAndClear(): List<TrackerState> {
    val result = storedFileStates
    storedFileStates = emptyList()
    wasLoaded = true
    return result
  }

  companion object {
    private const val DISABLED_FILES_THRESHOLD = 20

    private const val NODE_PARTIAL_FILE = "file"
    private const val ATT_PATH = "path"

    private const val NODE_VCS = "vcs"
    private const val NODE_CURRENT = "current"
    private const val ATT_CONTENT = "content"

    private const val NODE_RANGES = "ranges"
    private const val NODE_RANGE = "range"
    private const val ATT_START_1 = "start1"
    private const val ATT_END_1 = "end1"
    private const val ATT_START_2 = "start2"
    private const val ATT_END_2 = "end2"
    private const val ATT_CHANGELIST_ID = "changelist"

    @JvmStatic
    internal fun restoreState(project: Project) {
      if (!ChangeListManager.getInstance(project).areChangeListsEnabled()) return

      val fileStates = project.service<PartialLineStatusTrackerManagerState>().getStatesAndClear()
      if (fileStates.isEmpty()) return

      ChangeListManager.getInstance(project).invokeAfterUpdate(true) {
        LineStatusTrackerManager.getInstanceImpl(project).restoreTrackersForPartiallyChangedFiles(fileStates)
      }
    }

    @JvmStatic
    internal fun saveCurrentState(project: Project, fileStates: List<TrackerState>) {
      val stateService = project.service<PartialLineStatusTrackerManagerState>()
      stateService.storedFileStates = fileStates
    }

    private fun parseStates(element: Element): List<TrackerState> {
      val fileStates = mutableListOf<TrackerState>()
      for (node in element.getChildren(NODE_PARTIAL_FILE)) {
        val state = readPartialFileState(node)
        if (state != null) fileStates.add(state)
      }
      return fileStates
    }

    private fun writePartialFileState(state: TrackerState): Element {
      val element = Element(NODE_PARTIAL_FILE)
      element.setAttribute(ATT_PATH, state.virtualFile.path)

      if (state is FullTrackerState && Registry.`is`("vcs.enable.partial.changelists.persist.file.contents")) {
        // TODO: should not be stored in workspace.xml; Project.getProjectCachePath ?
        element.addContent(Element(NODE_VCS).setAttribute(ATT_CONTENT, XmlStringUtil.escapeIllegalXmlChars(state.vcsContent)))
        element.addContent(Element(NODE_CURRENT).setAttribute(ATT_CONTENT, XmlStringUtil.escapeIllegalXmlChars(state.currentContent)))
      }

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

      val vcsContent = element.getChild(NODE_VCS)?.getAttributeValue(ATT_CONTENT)
      val currentContent = element.getChild(NODE_CURRENT)?.getAttributeValue(ATT_CONTENT)

      val rangeStates = mutableListOf<RangeState>()

      val rangesNode = element.getChild(NODE_RANGES) ?: return null
      for (node in rangesNode.getChildren(NODE_RANGE)) {
        val rangeState = readRangeState(node) ?: return null
        rangeStates.add(rangeState)
      }

      if (vcsContent != null && currentContent != null) {
        return FullTrackerState(virtualFile, rangeStates,
                                XmlStringUtil.unescapeIllegalXmlChars(vcsContent),
                                XmlStringUtil.unescapeIllegalXmlChars(currentContent))
      }
      else {
        return TrackerState(virtualFile, rangeStates)
      }
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
}