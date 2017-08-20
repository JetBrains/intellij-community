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
package com.intellij.openapi.vcs.ex

import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.actions.AnnotationsSettings
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl
import com.intellij.openapi.vcs.changes.LocalChangeList
import com.intellij.openapi.vcs.changes.ui.ChangeListChooser
import com.intellij.openapi.vcs.ex.DocumentTracker.Block
import com.intellij.openapi.vcs.ex.PartialLocalLineStatusTracker.LocalRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Graphics
import java.awt.Point

class PartialLocalLineStatusTracker(project: Project,
                                    document: Document,
                                    virtualFile: VirtualFile,
                                    mode: Mode
) : LineStatusTracker<LocalRange>(project, document, virtualFile, mode) {
  private val changeListManager = ChangeListManagerImpl.getInstanceImpl(project)

  override val renderer = MyLineStatusMarkerRenderer(this)

  private val defaultMarker: ChangeListMarker

  init {
    defaultMarker = ChangeListMarker(changeListManager.defaultChangeList)
  }

  override fun Block.toRange(): LocalRange = LocalRange(this.start, this.end, this.vcsStart, this.vcsEnd, this.innerRanges,
                                                        (this.marker ?: defaultMarker).changelistId)


  protected class MyLineStatusMarkerRenderer(override val tracker: PartialLocalLineStatusTracker) :
    LineStatusTracker.LocalLineStatusMarkerRenderer(tracker) {

    override fun paint(editor: Editor, range: Range, g: Graphics) {
      super.paint(editor, range, g)

      if (range is LocalRange) {
        val markerColor = getMarkerColor(editor, range)
        if (markerColor != null) {
          val area = getMarkerArea(editor, range.line1, range.line2)

          val extraHeight = if (area.height != 0) 0 else JBUI.scale(3)
          val width = JBUI.scale(2)
          val x = area.x + area.width - width
          val y = area.y - extraHeight
          val height = area.height + 2 * extraHeight

          g.color = markerColor
          g.fillRect(x, y, width, height)
        }
      }
    }

    private fun getMarkerColor(editor: Editor, range: LocalRange): Color? {
      if (range.changelistId == tracker.defaultMarker.changelistId) return null

      val colors = AnnotationsSettings.getInstance().getAuthorsColors(editor.colorsScheme)
      val seed = range.changelistId.hashCode()
      return colors[Math.abs(seed % colors.size)]
    }

    override fun createToolbarActions(editor: Editor, range: Range, mousePosition: Point?): List<AnAction> {
      val actions = ArrayList<AnAction>()
      actions.addAll(super.createToolbarActions(editor, range, mousePosition))
      actions.add(SetChangeListAction(editor, range, mousePosition))
      return actions
    }

    private inner class SetChangeListAction(val editor: Editor, range: Range, val mousePosition: Point?)
      : RangeMarkerAction(range, IdeActions.MOVE_TO_ANOTHER_CHANGE_LIST) {
      override fun isEnabled(range: Range): Boolean = range is LocalRange

      override fun actionPerformed(range: Range) {
        tracker.moveToAnotherChangelist(range as LocalRange)

        val newRange = tracker.findRange(range)
        if (newRange != null) tracker.renderer.showHintAt(editor, newRange, mousePosition)
      }
    }
  }


  private fun moveToAnotherChangelist(range: LocalRange) {
    val block = findBlock(range) ?: return
    val oldListId = block.ourData.marker?.changelistId

    val oldChangeList = if (oldListId != null) ChangeListManager.getInstance(project).getChangeList(oldListId) else null
    val chooser = ChangeListChooser(project,
                                    ChangeListManager.getInstance(project).changeListsCopy,
                                    oldChangeList,
                                    ActionsBundle.message("action.ChangesView.Move.text"),
                                    null)
    chooser.show()

    val newChangelistId = chooser.selectedList?.id
    if (newChangelistId != null && newChangelistId != oldListId) {
      documentTracker.writeLock {
        block.marker = ChangeListMarker(newChangelistId)
      }

      ApplicationManager.getApplication().invokeLater {
        updateHighlighter(block)
      }
    }
  }


  class LocalRange(line1: Int, line2: Int, vcsLine1: Int, vcsLine2: Int, innerRanges: List<InnerRange>?,
                   val changelistId: String)
    : Range(line1, line2, vcsLine1, vcsLine2, innerRanges)

  protected data class ChangeListMarker(val changelistId: String) {
    constructor(changelist: LocalChangeList) : this(changelist.id)
  }

  protected data class MyBlockData(var marker: ChangeListMarker? = null) : LineStatusTrackerBase.BlockData()

  override fun createBlockData(): BlockData = MyBlockData()
  override val Block.ourData: MyBlockData get() = getBlockData(this) as MyBlockData

  private var Block.marker: ChangeListMarker?
    get() = this.ourData.marker
    set(value) {
      this.ourData.marker = value
    }

  companion object {
    @JvmStatic
    fun createTracker(project: Project,
                      document: Document,
                      virtualFile: VirtualFile,
                      mode: Mode): PartialLocalLineStatusTracker {
      return PartialLocalLineStatusTracker(project, document, virtualFile, mode)
    }
  }
}
