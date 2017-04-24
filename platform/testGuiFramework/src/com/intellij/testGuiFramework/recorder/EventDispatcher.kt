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
import com.intellij.testGuiFramework.recorder.ui.GuiScriptEditorFrame
import java.awt.Component
import java.awt.Container
import java.awt.KeyboardFocusManager
import java.awt.Point
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.awt.event.MouseEvent.MOUSE_PRESSED
import java.util.*
import javax.swing.JFrame
import javax.swing.KeyStroke
import javax.swing.RootPaneContainer
import javax.swing.SwingUtilities

/**
 * @author Sergey Karashevich
 */
object EventDispatcher {

  val mainActions by lazy { (ActionManager.getInstance().getAction(IdeActions.GROUP_MAIN_MENU) as ActionGroup).getFlatIdList() }

  val LOG = Logger.getInstance(EventDispatcher::class.java)
  fun processMouseEvent(me: MouseEvent) {

//        if (!(me.clickCount == 1 && me.id == MOUSE_CLICKED && me.button == BUTTON1)) return
    if (!(me.id == MOUSE_PRESSED)) return

    ScriptGenerator.flushTyping()

    var component: Component? = me.component
    val mousePoint = me.point

    if (component is JFrame)
      if (component.title == GuiScriptEditorFrame.GUI_SCRIPT_FRAME_TITLE) return // ignore mouse events from GUI Script Editor Frame

    if (component is RootPaneContainer) {

      val layeredPane = component.layeredPane
      val pt = SwingUtilities.convertPoint(component, mousePoint, layeredPane)
      component = layeredPane.findComponentAt(pt)
    }
    else if (component is Container) {
      component = component.findComponentAt(mousePoint)
    }

    if (component == null) {
      component = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
    }
    if (component != null) {
      val convertedPoint = Point(
        me.locationOnScreen.x - component.locationOnScreen.x,
        me.locationOnScreen.y - component.locationOnScreen.y)

      //get all extended code generators and get with the highest priority
      LOG.info("Delegate click from component:${component}")
      ScriptGenerator.clickComponent(component, convertedPoint, me)
    }
  }


  fun processKeyBoardEvent(keyEvent: KeyEvent) {
    if (keyEvent.component is JFrame && (keyEvent.component as JFrame).title == GuiScriptEditorFrame.GUI_SCRIPT_FRAME_TITLE) return
    if (keyEvent.id == KeyEvent.KEY_TYPED)
      ScriptGenerator.processTyping(keyEvent.keyChar)
    if (SystemInfo.isMac && keyEvent.id == KeyEvent.KEY_PRESSED) {
      //we are redirecting native Mac Preferences action as an Intellij action "Show Settings" has been invoked
      LOG.info(keyEvent.toString())
      val showSettingsId = "ShowSettings"
      if (KeymapManager.getInstance().activeKeymap.getActionIds(KeyStroke.getKeyStrokeForEvent(keyEvent)).contains(showSettingsId)) {
        val showSettingsAction = ActionManager.getInstance().getAction(showSettingsId)
        val actionEvent = AnActionEvent.createFromInputEvent(keyEvent, ActionPlaces.UNKNOWN, showSettingsAction.templatePresentation,
                                                             DataContext.EMPTY_CONTEXT)
        ScriptGenerator.processKeyActionEvent(showSettingsAction, actionEvent)
      }
    }
  }

  fun processActionEvent(anActionTobePerformed: AnAction, anActionEvent: AnActionEvent?) {
    val actMgr = ActionManager.getInstance()
    if (anActionEvent!!.inputEvent is KeyEvent) ScriptGenerator.processKeyActionEvent(anActionTobePerformed, anActionEvent)
    if (mainActions.contains(actMgr.getId(anActionTobePerformed))) ScriptGenerator.processMainMenuActionEvent(anActionTobePerformed,
                                                                                                              anActionEvent)
  }


  private fun ActionGroup.getFlatIdList(): List<String> {
    val actMgr = ActionManager.getInstance()
    val result = ArrayList<String>()
    this.getChildren(null).forEach { anAction ->
      if (anAction is ActionGroup) result.addAll(anAction.getFlatIdList())
      else result.add(actMgr.getId(anAction))
    }
    return result
  }
}