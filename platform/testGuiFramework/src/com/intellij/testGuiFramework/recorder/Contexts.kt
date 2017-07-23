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

import com.intellij.testGuiFramework.recorder.ui.GuiScriptEditorFrame
import java.awt.Component
import java.util.*
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JFrame

/**
 * @author Sergey Karashevich
 */
class Contexts() {

  enum class Type {DIALOG, WELCOME_FRAME, PROJECT_WIZARD, IDE_FRAME }
  enum class SubType {EDITOR, TOOL_WINDOW }

  private var projectWizardFound = false
  private var welcomeFrameFound = false
  private var ideFrameFound = false
  private var myToolWindowId: String? = null

  private val indentShift = 2
  private var indentLevel: Int = 0
  private val contextArray = ArrayList<Int>()

  fun check(cmp: Component) {

    val parent = (cmp as JComponent).rootPane.parent
    if (contextArray.isEmpty()) {
      checkGlobalContext(parent)
      checkDialogContext(parent)
      contextArray.add(parent.hashCode())
      indentLevel++
    }
    //check that we increase component level or decrease component level
    else {
      //hashcode equals -> no need to change components
      if (parent.hashCode() == contextArray.last())
      //do nothing
      else {
        //current context component and the previous to last in contextArray hashcodes are same
        if (contextArray.size > 1 && parent.hashCode() == contextArray[contextArray.size - 2]) {
          contextArray.removeAt(contextArray.size - 1)
          indentLevel--
          Writer.writeln(closeContext())
          assert(indentLevel >= 0)
        }
        else
          if (contextArray.size > 1 && parent.hashCode() != contextArray[contextArray.size - 2]) {
            //start a new context
            checkGlobalContext(parent)
            checkDialogContext(parent)
            contextArray.add(parent.hashCode())
            indentLevel++
            assert(indentLevel >= 0)
          }
          else
            if (contextArray.size == 1 && parent.hashCode() != contextArray.last()) {
              //start a new context
              checkGlobalContext(parent)
              checkDialogContext(parent)
              contextArray.add(parent.hashCode())
              indentLevel++
              assert(indentLevel >= 0)
            }
      }
    }
  }

  companion object {
    val IDE_FRAME_VAL = "ideFrame"
  }

  var globalContext: Type? = null
  var currentSubContextType: SubType? = null

  private fun checkGlobalContext(parent: Component) {
    if (parent is JFrame && parent.title == GuiScriptEditorFrame.GUI_SCRIPT_FRAME_TITLE) return //do nothing if switch to GUI Script Editor

    if (contextArray.isEmpty() || contextArray.first() != parent.hashCode()) {
      when (parent) {
        is com.intellij.openapi.wm.impl.welcomeScreen.FlatWelcomeFrame -> {
          globalContext = Type.WELCOME_FRAME
          indentLevel = 0
          if (contextArray.isNotEmpty()) {
            Writer.writeln(closeContext())
            contextArray.clear()
          }
          Writer.writeln(welcomeFrameStart())
          return
        }
        is JFrame -> {
          globalContext = Type.IDE_FRAME
          indentLevel = 0
          if (contextArray.isNotEmpty()) {
            Writer.writeln(closeContext())
            contextArray.clear()
          }
          Writer.writeln(ideFrameStart())
          return
        }
      }
    }
  }

  private fun checkDialogContext(parent: Component) {
    if (parent is JFrame && parent.title == GuiScriptEditorFrame.GUI_SCRIPT_FRAME_TITLE) return //do nothing if switch to GUI Script Editor
    when (parent) {
      is JDialog -> {
        if (parent.title == com.intellij.ide.IdeBundle.message("title.new.project")) {
          Writer.writeln(projectWizardContextStart())
          return
        }
        else {
          Writer.writeln(dialogContextStart(parent.title))
          return
        }
      }
    }
  }


  fun getIndent(): String {
    val size = indentLevel * indentShift
    val sb = StringBuilder()
    for (i in 1..size) sb.append(" ")
    return sb.toString()
  }

  //*********** Context Scripts ************

  fun closeContext() = "}"

  fun dialogContextStart(title: String): String {
    globalContext = Type.DIALOG
    val withDialog = Templates.withDialog(title)
    return withDialog
  }

  fun projectWizardContextStart(): String {
    globalContext = Type.PROJECT_WIZARD
    val withProjectWizard = Templates.withProjectWizard()
    projectWizardFound = true

    return withProjectWizard
  }

  fun welcomeFrameStart(): String {
    globalContext = Type.WELCOME_FRAME
    val withWelcomeFrame = Templates.withWelcomeFrame()
    welcomeFrameFound = true

    return withWelcomeFrame
  }

  fun ideFrameStart(): String {
    globalContext = Type.IDE_FRAME
    val withIdeFrame = Templates.withIdeFrame()
    ideFrameFound = true

    return withIdeFrame
  }

  fun editorActivate(): String {
    currentSubContextType = SubType.EDITOR
    return "EditorFixture(robot(), $IDE_FRAME_VAL).requestFocus()"
  }

  fun toolWindowActivate(toolWindowId: String? = null): String {
    currentSubContextType = SubType.TOOL_WINDOW
    myToolWindowId = toolWindowId
    return "ToolWindowFixture(\"$toolWindowId\", $IDE_FRAME_VAL.getProject(), robot()).activate()"
  }

  fun clear() {
    currentSubContextType = null
    contextArray.clear()
    indentLevel = 0
  }
}
