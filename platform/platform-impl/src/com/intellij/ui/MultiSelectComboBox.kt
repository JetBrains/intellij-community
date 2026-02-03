// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.ComboBoxWithWidePopup
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.EventQueue
import java.awt.FlowLayout
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.event.InputEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.Serializable
import java.util.function.Function
import javax.swing.AbstractListModel
import javax.swing.Box
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.MutableComboBoxModel
import javax.swing.plaf.basic.BasicComboBoxEditor
import javax.swing.plaf.basic.BasicComboBoxUI

/**
 * Combo-box with an ability to pick several items.
 *
 * Use addSelectionChangedListener() to subscribe on change events.
 * Use getSelectedItems() to get selected items explicitly.
 *
 * @param items The list of available items.
 * @param itemToTextConverter The function for extracting an item's presentable name.
 * @param selectedItems The list of initially selected values
 *
 * @author Anton Kozub
 */
@ApiStatus.Internal
class MultiSelectComboBox<T>(
  items: List<T>,
  private val itemToTextConverter: Function<T, String>? = null,
) : JComponent() {
  private var myActionEventIsFiring = false
  private var myItems = items.toMutableList()
  private val mySelectedItems = mutableSetOf<T>()
  private val myComboBox: ComboBox<T>
  private val mySelectedPanel: JPanel
  private val myComboBoxModel = MultiComboBoxModel(items)

  val items: List<T> = myItems
  val selectedItems: Set<T> = mySelectedItems

  var requestFocusOnClick: Boolean
    get() = myComboBox.isRequestFocusEnabled
    set(value) {
      myComboBox.isRequestFocusEnabled = value
    }

  constructor(
    items: Array<T>,
    itemToTextConverter: Function<T, String>? = null,
  ) : this(items.toList(), itemToTextConverter)

  init {
    layout = BorderLayout()

    mySelectedPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 2)).apply {
      isOpaque = false
    }

    myComboBox = ComboBox(myComboBoxModel).apply {
      isEditable = true

      editor = object : BasicComboBoxEditor() {
        override fun getEditorComponent(): Component {
          return mySelectedPanel
        }
      }

      (ui as BasicComboBoxUI).addEditor()

      @Suppress("HardCodedStringLiteral")
      setRenderer { _, item, _, _, _ -> JLabel(getItemText(item)) }

      addActionListener { handleSelectionInPopupMenu() }
    }

    add(myComboBox)

    updateSelectedDisplay()
  }

  fun setItems(newItems: List<T>) {
    myItems = newItems.toMutableList()
    setSelectedItems(mySelectedItems)
  }

  fun setItems(newItems: Array<T>) {
    setItems(newItems.toList())
  }

  fun getItemAt(index: Int): T? = myItems.elementAtOrNull(index)

  fun insertItemAt(item: T, index: Int) {
    myItems.add(index, item)
    setSelectedItems(mySelectedItems)
  }

  fun removeItem(item: T) {
    myItems.remove(item)
    setSelectedItems(mySelectedItems)
  }

  fun setSelectedItems(selectedItems: Set<T>?) {
    val existingSelectedItems = myItems.intersect(selectedItems ?: emptySet())
    mySelectedItems.clear()
    mySelectedItems.addAll(existingSelectedItems)

    myComboBoxModel.objects.clear()
    myComboBoxModel.objects.addAll(myItems - existingSelectedItems)
    updateSelectedDisplay()
  }

  fun setSelectedItems(selectedItems: Array<T>?) {
    setSelectedItems(selectedItems?.toSet())
  }

  fun setSelectedItems(selectedItems: Collection<T>?) {
    setSelectedItems(selectedItems?.toSet())
  }

  override fun setEnabled(enabled: Boolean) {
    super.setEnabled(enabled)
    myComboBox.isEnabled = enabled
    updateSelectedDisplay()
  }

  fun addActionListener(listener: ActionListener) {
    listenerList.add(ActionListener::class.java, listener)
  }

  fun removeActionListener(listener: ActionListener) {
    listenerList.remove(ActionListener::class.java, listener)
  }

  private fun fireActionEvent() {
    if (myActionEventIsFiring) return

    myActionEventIsFiring = true

    try {
      val listeners = getListeners(ActionListener::class.java)
      if (listeners.isEmpty()) return

      val mostRecentEventTime = EventQueue.getMostRecentEventTime()
      val currentEvent = EventQueue.getCurrentEvent()

      val modifiers = when (currentEvent) {
        is InputEvent -> currentEvent.modifiersEx
        is ActionEvent -> currentEvent.modifiers
        else -> 0
      }

      val event = ActionEvent(this, ActionEvent.ACTION_PERFORMED, "comboBoxChanged", mostRecentEventTime, modifiers)

      listeners.forEach { it.actionPerformed(event) }
    }
    finally {
      myActionEventIsFiring = false
    }
  }

  private fun handleSelectionInPopupMenu() {
    val selectedItem = myComboBox.selectedItem as? T ?: return

    if (selectedItem !in mySelectedItems && selectedItem in myComboBoxModel)
      selectItem(selectedItem)

    myComboBox.selectedItem = null
  }

  private fun selectItem(item: T) {
    mySelectedItems.add(item)
    myComboBoxModel.removeElement(item)
    updateSelectedDisplay()
    fireActionEvent()
  }

  private fun deselectItem(item: T) {
    mySelectedItems.remove(item)
    myComboBoxModel.addElement(item)
    updateSelectedDisplay()
    fireActionEvent()
  }

  private fun updateSelectedDisplay() {
    mySelectedPanel.removeAll()
    mySelectedItems.forEachIndexed { index, item ->
      if (index > 0)
        mySelectedPanel.add(Box.createRigidArea(Dimension(5, 0)))

      val itemPanel = JPanel(FlowLayout(FlowLayout.LEFT, 2, 0)).apply {
        isOpaque = true
        border = JBUI.Borders.emptyTop(2)
        isEnabled = myComboBox.isEnabled
        @Suppress("HardCodedStringLiteral")
        val textLabel = JLabel(getItemText(item)).apply {
          isEnabled = myComboBox.isEnabled
        }
        add(textLabel)

        val closeLabel = JLabel().apply {
          icon = AllIcons.Actions.Close
          addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent?) {
              if(!myComboBox.isEnabled) return
              deselectItem(item)
            }
          })
        }
        add(closeLabel)
      }
      mySelectedPanel.add(itemPanel)
    }
    mySelectedPanel.revalidate()
    mySelectedPanel.repaint()
  }

  @NonNls
  private fun getItemText(item: T): String = itemToTextConverter?.apply(item) ?: item.toString()

  private class MultiComboBoxModel<T> : AbstractListModel<T>, MutableComboBoxModel<T>, Serializable {
    val objects: MutableList<T>
    var selectedObject: Any? = null

    constructor(items: Collection<T>) {
      objects = items.toMutableList()
    }

    override fun setSelectedItem(item: Any?) {
      if ((selectedObject != null && selectedObject != item) ||
          selectedObject == null && item != null) {
        selectedObject = item
        fireContentsChanged(this, -1, -1)
      }
    }

    override fun getSelectedItem(): Any? = selectedObject

    override fun getSize(): Int = objects.size

    override fun getElementAt(index: Int): T? =
      if (index in 0..<objects.size) objects.elementAt(index)
      else null

    override fun addElement(element: T) {
      objects.add(element)
      fireIntervalAdded(this, objects.size - 1, objects.size - 1)
    }

    override fun insertElementAt(element: T, index: Int) {
      objects.add(index, element)
      fireIntervalAdded(this, index, index)
    }

    override fun removeElementAt(index: Int) {
      objects.removeAt(index)
      fireIntervalRemoved(this, index, index)
    }

    override fun removeElement(element: Any?) {
      val index = objects.indexOf(element)
      if (index != -1) {
        removeElementAt(index)
      }
    }

    operator fun contains(element: T): Boolean = objects.contains(element)
  }
}