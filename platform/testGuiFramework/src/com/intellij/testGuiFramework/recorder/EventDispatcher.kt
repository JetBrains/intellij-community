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
package com.intellij.testGuiFramework.recorder

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.util.SystemInfo
import java.awt.Component
import java.awt.Container
import java.awt.KeyboardFocusManager
import java.awt.Point
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.awt.event.MouseEvent.*
import java.util.*
import javax.swing.JFrame
import javax.swing.KeyStroke
import javax.swing.RootPaneContainer
import javax.swing.SwingUtilities

object EventDispatcher {

  private val LOG = Logger.getInstance("#${EventDispatcher::class.qualifiedName}")
  private val MAC_NATIVE_ACTIONS = arrayOf("ShowSettings", "EditorEscape")

  object SelectionProcessor {
    private var firstEvent: MouseEvent? = null

    fun processDragging(event: MouseEvent) {
      if (firstEvent == null) firstEvent = event
    }

    fun stopDragging(event: MouseEvent) {
      if (firstEvent != null) {
        processSelection(firstEvent!!, event)
        firstEvent = null
      }
    }

    private fun processSelection(firstEvent: MouseEvent, lastEvent: MouseEvent) {
      val firstComponent: Component? = findComponent(firstEvent)
      val lastComponent: Component? = findComponent(lastEvent)

      //drag and drop between components is not supported yet
      if (lastComponent != firstComponent) return

      if (lastComponent != null) {
        val firstPoint = getPoint(firstEvent, lastComponent)
        val lastPoint = getPoint(lastEvent, lastComponent)
        ScriptGenerator.selectInComponent(lastComponent, firstPoint, lastPoint, lastEvent)
      }
    }
  }

  fun processMouseEvent(event: MouseEvent) {
    if (isMainFrame(event.component)) return
    when(event.id) {
      MOUSE_PRESSED -> { processClick(event) }
      MOUSE_DRAGGED -> { SelectionProcessor.processDragging(event) }
      MOUSE_RELEASED -> { SelectionProcessor.stopDragging(event) }
    }
  }

  private fun processClick(event: MouseEvent){
    val actualComponent: Component? = findComponent(event)
    if (actualComponent != null) {
      LOG.info("Delegate click from component:${actualComponent}")
      val convertedPoint = Point(event.locationOnScreen.x - actualComponent.locationOnScreen.x,
                                 event.locationOnScreen.y - actualComponent.locationOnScreen.y)
      ScriptGenerator.clickComponent(actualComponent, convertedPoint, event)
    }
  }

  private fun getPoint(event: MouseEvent, component: Component): Point {
    return Point(event.locationOnScreen.x - component.locationOnScreen.x, event.locationOnScreen.y - component.locationOnScreen.y)
  }

  private fun findComponent(event: MouseEvent): Component? {
    val mousePoint = event.point
    val eventComponent = event.component
    var actualComponent: Component? = null
    when (eventComponent) {
      is RootPaneContainer -> {
        val layeredPane = eventComponent.layeredPane
        val point = SwingUtilities.convertPoint(eventComponent, mousePoint, layeredPane)
        actualComponent = layeredPane.findComponentAt(point)
      }
      is Container -> actualComponent = eventComponent.findComponentAt(mousePoint)
    }
    if (actualComponent == null) actualComponent = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
    return actualComponent
  }

  fun processKeyBoardEvent(keyEvent: KeyEvent) {
    if (isMainFrame(keyEvent.component)) return

    if (keyEvent.id == KeyEvent.KEY_TYPED) ScriptGenerator.processTyping(keyEvent)
    if (SystemInfo.isMac && keyEvent.id == KeyEvent.KEY_PRESSED) {
      //we are redirecting native Mac action as an Intellij actions
      LOG.info(keyEvent.toString())
      val actionIds = KeymapManager.getInstance().activeKeymap.getActionIds(KeyStroke.getKeyStrokeForEvent(keyEvent))
      for(actionId in actionIds){
        if(isMacNativeAction(keyEvent)) {
          val action = ActionManager.getInstance().getAction(actionId)
          val actionEvent = AnActionEvent.createFromInputEvent(keyEvent, ActionPlaces.UNKNOWN, action.templatePresentation,
                                                               DataContext.EMPTY_CONTEXT)
          ScriptGenerator.processKeyActionEvent(action, actionEvent)
        }
      }
    }
  }

  fun processActionEvent(action: AnAction, event: AnActionEvent?) {
    if (event == null) return

    val inputEvent = event.inputEvent
    if (inputEvent is KeyEvent) {
      if(!isMacNativeAction(inputEvent)) {
        ScriptGenerator.processKeyActionEvent(action, event)
      }
    } else {
      val actionManager = ActionManager.getInstance()
      val mainActions = (actionManager.getAction(IdeActions.GROUP_MAIN_MENU) as ActionGroup).getFlatIdList()
      if (mainActions.contains(actionManager.getId(action))) ScriptGenerator.processMainMenuActionEvent(action, event)
    }
  }

  private fun ActionGroup.getFlatIdList(): List<String> {
    val actionManager = ActionManager.getInstance()
    val result = ArrayList<String>()
    this.getChildren(null).forEach { action ->
      if (action is ActionGroup) result.addAll(action.getFlatIdList())
      else result.add(actionManager.getId(action))
    }
    return result
  }

  private fun isMainFrame(component: Component?): Boolean {
    return component is JFrame && component.title == "GUI Script Editor"
  }

  private fun isMacNativeAction(keyEvent: KeyEvent): Boolean{
    if (!SystemInfo.isMac) return false
    val actionIds = KeymapManager.getInstance().activeKeymap.getActionIds(KeyStroke.getKeyStrokeForEvent(keyEvent))
    return actionIds.any { it in MAC_NATIVE_ACTIONS }
  }
}