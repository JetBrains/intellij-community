// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.frame

import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.ui.popup.ListItemDescriptor
import com.intellij.ui.CollectionListModel
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.components.JBList
import com.intellij.ui.popup.list.GroupedItemsListRenderer
import com.intellij.util.ui.NamedColorUtil
import com.intellij.util.ui.UIUtil
import com.intellij.xdebugger.XDebuggerBundle
import com.intellij.xdebugger.frame.XExecutionStack
import org.jetbrains.annotations.Nls
import java.awt.Component
import java.awt.Point
import javax.swing.*
import javax.swing.plaf.FontUIResource

class XDebuggerThreadsList(private val renderer: ListCellRenderer<StackInfo>) : JBList<StackInfo>(
    CollectionListModel()
) {
    private var mySelectedFrame: StackInfo? = null

    val elementCount: Int
        get() = model.size

    companion object {
        val THREADS_LIST: DataKey<XDebuggerThreadsList> = DataKey.create("THREADS_LIST")

        fun createDefault(): XDebuggerThreadsList {
            return create(XDebuggerGroupedFrameListRenderer())
        }

        fun create(renderer: ListCellRenderer<StackInfo>): XDebuggerThreadsList {
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
        setDataProvider {
            return@setDataProvider if (THREADS_LIST.`is`(it)) this@XDebuggerThreadsList else null
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

    private class XDebuggerGroupedFrameListRenderer : GroupedItemsListRenderer<StackInfo>(XDebuggerListItemDescriptor()) {
        private val myOriginalRenderer = XDebuggerThreadsListRenderer()

        init {
            mySeparatorComponent.setCaptionCentered(false)
        }

        override fun getListCellRendererComponent(
            list: JList<out StackInfo>?,
            value: StackInfo?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean
        ): Component {
            return myOriginalRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
        }

        override fun createItemComponent(): JComponent {
            createLabel()
            return XDebuggerThreadsListRenderer()
        }
    }

    private class XDebuggerListItemDescriptor : ListItemDescriptor<StackInfo> {
        override fun getTextFor(value: StackInfo?): String? = value?.getText()
        override fun getTooltipFor(value: StackInfo?): String? = value?.getText()

        override fun getIconFor(value: StackInfo?): Icon? = value?.stack?.icon

        override fun hasSeparatorAboveOf(value: StackInfo?): Boolean = false
        override fun getCaptionAboveOf(value: StackInfo?): String? = null
    }

    private class XDebuggerThreadsListRenderer : ColoredListCellRenderer<StackInfo>() {

        override fun customizeCellRenderer(
            list: JList<out StackInfo>,
            value: StackInfo?,
            index: Int,
            selected: Boolean,
            hasFocus: Boolean
        ) {
            val stack = value ?: return
            if (selected) {
                background = UIUtil.getListSelectionBackground(hasFocus)
                foreground = NamedColorUtil.getListSelectionForeground(hasFocus)
                mySelectionForeground = foreground
            }
            when (stack.kind) {
                StackInfo.StackKind.ExecutionStack -> {
                    append(stack.getText())
                    icon = stack.stack?.icon
                }
                StackInfo.StackKind.Error,
                StackInfo.StackKind.Loading -> append(stack.getText())
            }
        }
    }
}

data class StackInfo private constructor(val kind: StackKind, val stack: XExecutionStack?, @Nls val error: String?) {
  companion object {
    fun from(executionStack: XExecutionStack): StackInfo = StackInfo(StackKind.ExecutionStack, executionStack, null)
    fun error(@Nls error: String): StackInfo = StackInfo(StackKind.Error, null, error)

    val loading = StackInfo(StackKind.Loading, null, null)
  }

  @Nls
  fun getText(): String {
    return when (kind) {
      StackKind.ExecutionStack -> stack!!.displayName
      StackKind.Error -> error!!
      StackKind.Loading -> XDebuggerBundle.message("stack.frame.loading.text")
    }
  }

  override fun toString(): String = getText()

  enum class StackKind {
    ExecutionStack,
    Error,
    Loading
  }
}
