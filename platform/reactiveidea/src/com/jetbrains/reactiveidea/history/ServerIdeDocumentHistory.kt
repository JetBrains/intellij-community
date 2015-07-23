/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.jetbrains.reactiveidea.history

import com.google.gson.*
import com.google.gson.reflect.TypeToken
import com.intellij.openapi.command.CommandAdapter
import com.intellij.openapi.command.CommandEvent
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.impl.CommandMerger
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.CaretAdapter
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.DocumentAdapter
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.fileEditor.*
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory
import com.intellij.openapi.fileEditor.impl.IdeDocumentHistoryImpl
import com.intellij.openapi.fileEditor.impl.IdeDocumentHistoryImpl.PlaceInfo
import com.intellij.openapi.fileEditor.impl.text.TextEditorState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileAdapter
import com.intellij.openapi.vfs.VirtualFileEvent
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.xmlb.XmlSerializer
import com.jetbrains.reactiveidea.EditorHost
import com.jetbrains.reactiveidea.history.host.getHead
import com.jetbrains.reactiveidea.history.host.getHistoryList
import com.jetbrains.reactiveidea.history.host.historyHost
import com.jetbrains.reactivemodel.ReactiveModel
import com.jetbrains.reactivemodel.getIn
import com.jetbrains.reactivemodel.models.MapModel
import com.jetbrains.reactivemodel.util.get
import com.thoughtworks.xstream.XStream
import org.jetbrains.annotations.NotNull
import java.lang.reflect.Type
import java.net.URL
import java.util.*
import com.jetbrains.reactiveidea.history.host.*


public class ServerIdeDocumentHistory(project: Project,
                                      editorFactory: EditorFactory,
                                      editorManager: FileEditorManagerEx,
                                      val vfManager: VirtualFileManager,
                                      cmdProcessor: CommandProcessor,
                                      toolWindowManager: ToolWindowManager)
: IdeDocumentHistoryImpl(project, editorFactory, editorManager, vfManager, cmdProcessor, toolWindowManager) {

  private val history: List<PlaceInfo>
    get() {
      val cur = model ?: return emptyList()
      return getHistoryList(cur.root)
    }

  private val head: Int?
    get() {
      val cur = model ?: return null
      return getHead(cur.root)
    }

  private val model: ReactiveModel?
    get() = ReactiveModel.current()

  override fun clearHistory() {
    throw UnsupportedOperationException()
  }

  override fun back() {
    val prevPlace = head ?: return
    val reactiveModel = model ?: return
    removeInvalidFilesFromStacks()
    val previous = history[prevPlace]
    if (history.size() < 2) {
      throw IllegalStateException("History doesn't contain current place")
    }
    myBackInProgress = true
    try {
      reactiveModel.transaction { m ->
        back(m)
      }
      executeCommand {
        gotoPlaceInfo(previous)
      }
    } finally {
      myBackInProgress = false
    }
  }

  override fun forward() {
    val reactiveModel = model ?: return
    removeInvalidFilesFromStacks()
    val target = getTargetForwardInfo() ?: return
    myForwardInProgress = true
    try {
      reactiveModel.transaction { m ->
        forward(m)
      }
      executeCommand {
        gotoPlaceInfo(target)
      }
    } finally {
      myForwardInProgress = false
    }
  }

  override fun removeInvalidFilesFromStacks() {
    super.removeInvalidFilesFromStacks()
    val reactiveModel = model ?: return
    reactiveModel.transaction { m ->
      removeInvalidInfos(m)
    }
  }

  override fun onCommandStarted() {
    super.onCommandStarted()
    if (myCommandStartPlace != null) {
      if (history.isEmpty()) {
        model?.transaction { m ->
          addCurPlace(m, myCommandStartPlace)
        }
      }
    }
  }

  override fun onCommandFinished(commandGroupId: Any?) {
    if (myCommandStartPlace != null) {
      if (myCurrentCommandIsNavigation && myCurrentCommandHasMoves) {
        if (!myBackInProgress && !myForwardInProgress) {
          if (!CommandMerger.canMergeGroup(commandGroupId, myLastGroupId)) {
            model?.transaction { m ->
              val cur = currentPlace(m) ?: throw IllegalStateException("Current shouldn't be null")
              val current = getCurrentPlaceInfo()
              if (!IdeDocumentHistoryImpl.isSame(cur, current)) {
                forward(m)
              }
              addCurPlace(m, current)
              ensureSize(m, IdeDocumentHistoryImpl.BACK_QUEUE_LIMIT)
            }
          }
        }
        removeInvalidFilesFromStacks()
      }
    }
    myLastGroupId = commandGroupId

    if (myCurrentCommandHasChanges) {
      setCurrentChangePlace()
    } else if (myCurrentCommandHasMoves) {
      pushCurrentChangePlace()
    }
  }

  override fun isForwardAvailable(): Boolean {
    val curHead = head ?: return false
    return history.size() > (curHead + 1)
  }

  override fun isBackAvailable(): Boolean {
    return head != null
  }

  private fun getTargetForwardInfo(): PlaceInfo? {
    var prev = head ?: -1;
    var new = prev + 2
    if (new < history.size()) {
      return history[new]
    }
    return null
  }

  protected fun executeCommand(runnable: () -> Unit) {
    return executeCommand(runnable, "", null)
  }
}
