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

import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.diagnostic.Logger
import com.intellij.testGuiFramework.recorder.ui.GuiScriptEditorPanel
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent

/**
 * @author Sergey Karashevich
 */

object GlobalActionRecorder {

  private val LOG = Logger.getInstance("#${GlobalActionRecorder::class.qualifiedName}")

  var isActive = false
    private set

  private val globalActionListener = object : AnActionListener {
    override fun beforeActionPerformed(action: AnAction?, dataContext: DataContext?, event: AnActionEvent?) {
      if (event?.place == GuiScriptEditorPanel.GUI_SCRIPT_EDITOR_PLACE) return //avoid GUI Script Editor Actions
      if(action == null) return
      EventDispatcher.processActionEvent(action, event)
      LOG.info("IDEA is going to perform action ${action.templatePresentation.text}")
    }

    override fun beforeEditorTyping(c: Char, dataContext: DataContext?) {
      LOG.info("IDEA typing detected: ${c}")
    }

    override fun afterActionPerformed(action: AnAction?, dataContext: DataContext?, event: AnActionEvent?) {
      if (event?.place == GuiScriptEditorPanel.GUI_SCRIPT_EDITOR_PLACE) return //avoid GUI Script Editor Actions
      if (action == null) return
      LOG.info("IDEA action performed ${action.templatePresentation.text}")
    }
  }

  private val globalAwtProcessor = IdeEventQueue.EventDispatcher { awtEvent ->
    when (awtEvent) {
      is MouseEvent -> EventDispatcher.processMouseEvent(awtEvent)
      is KeyEvent -> EventDispatcher.processKeyBoardEvent(awtEvent)
    }
    false
  }

  fun activate() {
    if (isActive) return
    LOG.info("Global action recorder is active")
    ActionManager.getInstance().addAnActionListener(globalActionListener)
    IdeEventQueue.getInstance().addDispatcher(globalAwtProcessor, GuiRecorderManager.frame) //todo: add disposal dependency on component
    isActive = true
  }

  fun deactivate() {
    if (isActive) {
      LOG.info("Global action recorder is non active")
      ActionManager.getInstance().removeAnActionListener(globalActionListener)
      IdeEventQueue.getInstance().removeDispatcher(globalAwtProcessor)
    }
    isActive = false
    ContextChecker.clearContext()
  }

}