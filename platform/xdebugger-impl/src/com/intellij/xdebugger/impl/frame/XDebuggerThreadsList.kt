// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.frame

import com.intellij.ide.IdeBundle
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.ui.popup.ListItemDescriptor
import com.intellij.ui.CollectionListModel
import com.intellij.ui.components.JBList
import com.intellij.ui.popup.list.GroupedItemsListRenderer
import com.intellij.xdebugger.XDebuggerBundle
import com.intellij.xdebugger.frame.XExecutionStack
import com.intellij.xdebugger.frame.XExecutionStack.AdditionalDisplayInfo
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.Component
import java.awt.Point
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionListener
import javax.swing.*
import javax.swing.plaf.FontUIResource


class XDebuggerThreadsList(
  private val renderer: ListCellRenderer<StackInfo>,
) : JBList<StackInfo>(CollectionListModel()), UiDataProvider {
  private var mySelectedFrame: StackInfo? = null

  /**
   * Deprecated.
   * Use [com.intellij.xdebugger.frame.XExecutionStack.SELECTED_STACKS] data key to get a set of selected stacks from the data context.
   */
  @ApiStatus.Obsolete
  var stackUnderMouse: StackInfo? = null
    private set

  val elementCount: Int
    get() = model.size

  companion object {
    val THREADS_LIST: DataKey<XDebuggerThreadsList> = DataKey.create("THREADS_LIST")

    fun createDefault(withDescription: Boolean): XDebuggerThreadsList {
      val renderer = if (withDescription) XDebuggerGroupedFrameListRendererWithDescription() else XDebuggerGroupedFrameListRenderer()
      val list = XDebuggerThreadsList(renderer)
      list.doInit()
      return list
    }
  }

  init {
    // This is a workaround for the performance issue IDEA-187063
    // default font generates too much garbage in deriveFont
    val font = font
    if (font != null) {
      setFont(FontUIResource(font.name, font.style, font.size))
    }
  }

  override fun uiDataSnapshot(sink: DataSink) {
    sink[THREADS_LIST] = this
    stackUnderMouse?.stack?.let {
      sink[XExecutionStack.SELECTED_STACKS] = listOf(it)
    }
  }

  private fun doInit() {
    selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
    cellRenderer = renderer
    selectionModel.addListSelectionListener { e ->
      if (!e.valueIsAdjusting) {
        onThreadChanged(selectedValue)
      }
    }

    addMouseMotionListener(object : MouseMotionListener {
      override fun mouseMoved(e: MouseEvent) {
        val point = e.point
        val index: Int = locationToIndex(point)
        if (index == -1) {
          stackUnderMouse = null
        }
        else {
          stackUnderMouse = model.getElementAt(index)
        }
      }

      override fun mouseDragged(e: MouseEvent) {}
    })

    emptyText.text = XDebuggerBundle.message("threads.list.threads.not.available")
  }

  private fun onThreadChanged(stack: StackInfo?) {
    if (mySelectedFrame != stack) {
      SwingUtilities.invokeLater { this.repaint() }

      mySelectedFrame = stack
    }
  }

  override fun getModel(): CollectionListModel<StackInfo> {
    return super.getModel() as CollectionListModel<StackInfo>
  }

  override fun setModel(model: ListModel<StackInfo>?) {
    // todo throw exception?
    // do not allow to change model (e.g. to FilteringListModel)
  }

  override fun locationToIndex(location: Point): Int {
    return if (location.y <= preferredSize.height) super.locationToIndex(location) else -1
  }

  fun clear() {
    model.removeAll()
  }

  private open class XDebuggerGroupedFrameListRenderer : GroupedItemsListRenderer<StackInfo>(XDebuggerListItemDescriptor()) {

    init {
      mySeparatorComponent.setCaptionCentered(false)
    }

    override fun getListCellRendererComponent(
      list: JList<out StackInfo>,
      value: StackInfo,
      index: Int,
      isSelected: Boolean,
      cellHasFocus: Boolean,
    ): Component {
      @Suppress("UNCHECKED_CAST") val renderer = itemComponent as? ListCellRenderer<StackInfo> ?: throw IllegalStateException("Invalid component $itemComponent")
      return renderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
    }

    override fun createItemComponent(): JComponent {
      createLabel()
      return XDebuggerThreadsListRenderer()
    }
  }

  private class XDebuggerGroupedFrameListRendererWithDescription : XDebuggerGroupedFrameListRenderer() {

    override fun createItemComponent(): JComponent {
      createLabel()
      return XDebuggerThreadsListRendererWithDescription()
    }
  }

  private class XDebuggerListItemDescriptor : ListItemDescriptor<StackInfo> {
    override fun getTextFor(value: StackInfo?): String? = value?.displayText
    override fun getTooltipFor(value: StackInfo?): String? = value?.displayText

    override fun getIconFor(value: StackInfo?): Icon? = value?.icon

    override fun hasSeparatorAboveOf(value: StackInfo?): Boolean = false
    override fun getCaptionAboveOf(value: StackInfo?): String? = null
  }
}

data class StackInfo internal constructor(
  @Nls val displayText: String,
  val icon: Icon?,
  @Nls val additionalDisplayText: AdditionalDisplayInfo?,
  val stack: XExecutionStack?
) {

  companion object {
    fun from(executionStack: XExecutionStack): StackInfo = StackInfo(executionStack.displayName, executionStack.icon, executionStack.additionalDisplayInfo, executionStack)
    fun error(@Nls error: String): StackInfo = StackInfo(error, null, null, null)

    val LOADING: StackInfo = StackInfo(XDebuggerBundle.message("stack.frame.loading.text"), null, null, null)
  }

  @Volatile
  @Nls
  internal var description: String = if (stack == null) "" else IdeBundle.message("progress.text.loading")

  override fun toString(): String = displayText
}
