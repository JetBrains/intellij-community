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

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.wm.impl.FocusManagerImpl
import com.intellij.testGuiFramework.generators.ComponentCodeGenerator
import com.intellij.testGuiFramework.generators.ComponentSelectionCodeGenerator
import com.intellij.testGuiFramework.generators.Generators
import com.intellij.testGuiFramework.recorder.ui.KeyUtil
import com.intellij.testGuiFramework.util.Key
import com.intellij.testGuiFramework.util.Modifier
import com.intellij.testGuiFramework.util.resolveKey
import com.intellij.ui.KeyStrokeAdapter
import java.awt.Component
import java.awt.Point
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import javax.swing.KeyStroke
import javax.swing.KeyStroke.getKeyStrokeForEvent

object ScriptGenerator {
  private val generators: List<ComponentCodeGenerator<*>> = Generators.getGenerators()

  fun processTyping(keyEvent: KeyEvent) {
    ContextChecker.checkContext(FocusManagerImpl.getGlobalInstance().focusOwner, keyEvent)
    Typer.type(keyEvent)
  }

  fun processKeyActionEvent(action: AnAction, event: AnActionEvent) {
    val keyEvent = event.inputEvent as KeyEvent
    val actionId = event.actionManager.getId(action)
    if (actionId != null) {
      if (ignore(actionId)) return
      addToScript("""//invokeAction("$actionId")""")
    }

    val keyStroke = getKeyStrokeForEvent(keyEvent)
    val keyStrokeStr = KeyStrokeAdapter.toString(keyStroke)
    if (ignore(keyStrokeStr)) return
    addToScript("""shortcut(${convertKeyStrokeToEnums(keyStroke)})""")
  }

  fun clickComponent(component: Component, convertedPoint: Point, mouseEvent: MouseEvent) {
    if (!isPopupList(component)) {
      ContextChecker.checkContext(component, mouseEvent)
    }

    val suitableGenerator = generators.filter { generator -> generator.accept(component) }.sortedByDescending(
      ComponentCodeGenerator<*>::priority).firstOrNull() ?: return
    val code = suitableGenerator.generateCode(component, mouseEvent, convertedPoint)
    addToScript(code)
  }

  fun processMainMenuActionEvent(action: AnAction, event: AnActionEvent) {
    val prohibitedPlaces = setOf("NavBarToolbar", "MainToolbar", "DebuggerToolbar")
    val actionId: String? = ActionManager.getInstance().getId(action)
    if (actionId == null) return
    if (event.place != "MainMenu") {
      if (prohibitedPlaces.contains(event.place)) return
      addToScript("//invokeAction(\"$actionId\")")
      return
    }
    val normalizedActionId = actionId.replace("\$","\\$")
    addToScript("""invokeMainMenu("$normalizedActionId")""")
  }

  fun addToScript(code: String) {
    Typer.flushBuffer()
    Writer.writeWithIndent(code)
  }

  private fun isPopupList(component: Component) = component.javaClass.name.toLowerCase().contains("listpopup")

  private fun ignore(actionOrShortCut: String): Boolean {
    val ignoreActions = listOf("EditorBackSpace")
    val ignoreShortcuts = listOf("space")
    return ignoreActions.contains(actionOrShortCut) || ignoreShortcuts.contains(actionOrShortCut)
  }

  fun selectInComponent(component: Component,
                        firstPoint: Point,
                        lastPoint: Point,
                        lastEvent: MouseEvent) {
    ContextChecker.checkContext(component, lastEvent)

    val suitableGenerator = generators.filter { generator ->
      generator.accept(component) && generator is ComponentSelectionCodeGenerator
    }.sortedByDescending(ComponentCodeGenerator<*>::priority).firstOrNull() ?: return

    if (suitableGenerator is ComponentSelectionCodeGenerator) {
      val code = suitableGenerator.generateSelectionCode(component, firstPoint, lastPoint)
      addToScript(code)
    }
  }
}

private fun convertKeyStrokeToEnums(keyStroke: KeyStroke): String {
  val hasModifiers = keyStroke.modifiers != 0
  val modifiersAndKeys = KeyStrokeAdapter.toString(keyStroke).toUpperCase().split(" ")
  val modifiers = arrayListOf<Modifier>()
  var key: Key? = null
  modifiersAndKeys.forEach {
    if (hasModifiers) try { modifiers.add(Modifier.valueOf(it)) } catch (e: IllegalArgumentException) {}
    try { key = resolveKey(it) } catch (e: IllegalArgumentException) {}
  }
  val sb = StringBuffer()
  modifiers.forEach { sb.append(it.name); sb.append(" + ") }
  sb.append(key ?: "NULL")
  return sb.toString()
}

private object Typer {
  private val strBuffer = StringBuilder()

  fun type(keyEvent: KeyEvent) {
    if(keyEvent.keyCode == KeyEvent.VK_BACK_SPACE && !strBuffer.isEmpty()){
      strBuffer.setLength(strBuffer.length - 1)
    } else {
      strBuffer.append(KeyUtil.patch(keyEvent.keyChar))
    }
  }

  fun flushBuffer() {
    if (strBuffer.isEmpty()) return
    Writer.writeWithIndent("""typeText("${strBuffer}")""")
    strBuffer.setLength(0)
  }
}