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

import com.intellij.openapi.wm.impl.FocusManagerImpl
import com.intellij.testGuiFramework.generators.*
import java.awt.Component
import java.awt.Point
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.util.*
import javax.swing.JComponent

/**
 * @author Sergey Karashevich
 */


object ContextChecker {
  private val globalContextGenerators: List<ContextCodeGenerator<*>> = Generators.getGlobalContextGenerators()
  private val localContextGenerators: List<ContextCodeGenerator<*>> = Generators.getLocalContextCodeGenerator()
  private val writer: (String) -> Unit = { code -> ScriptGenerator.addToScript(code) }
  private val contextTree: ContextTree = ContextTree(writer)

  fun getContextDepth(): Int = contextTree.getSize()
  fun clearContext() = contextTree.clear()

  fun checkContext(component: Component, inputEvent: InputEvent) {
    val globalContext = getGlobalApplicableContext(component)
    val localContextList: List<Context> = getLocalApplicableContext(component)
    contextTree.checkAliveContexts(inputEvent)
    if (globalContext != null) contextTree.addContext(globalContext)
    if (!localContextList.isNullOrEmpty()) contextTree.addContexts(localContextList)
  }

  private fun List<*>?.isNullOrEmpty(): Boolean = this == null || this.isEmpty()

  private fun getGlobalApplicableContext(component: Component): Context? =
    globalContextGenerators.filter { generator -> generator.accept(component) }.sortedByDescending(
      ContextCodeGenerator<*>::priority).firstOrNull()?.buildContext(component)


  private fun getLocalApplicableContext(component: Component): List<Context> =
    localContextGenerators.filter { generator -> generator.accept(component) }
      .sortedBy(ContextCodeGenerator<*>::priority)
      .map { applicableGenerator -> applicableGenerator.buildContext(component) }
}

private class ContextTree(val writeFun: (String) -> Unit) {
  private val myContextsTree: ArrayList<Context> = ArrayList()
  private var lastContext: Context? = null

  fun checkAliveContexts(inputEvent: InputEvent) {
    for (i in 0..myContextsTree.lastIndex) {
      if (!myContextsTree[i].isAlive(inputEvent)) {
        // from i to myContextsTree.lastIndex contexts should be dropped
        while (myContextsTree.lastIndex >= i) removeLastContext()
        break
      }
    }
  }

  fun addContext(context: Context) {
    if (context.inContextTree()) return //do nothing if the context is the same
    lastContext = context
    writeFun(context.code)
    myContextsTree.add(context)
  }

  fun getSize() = myContextsTree.size

  fun clear() {
    lastContext = null
    myContextsTree.clear()
  }

  fun addContexts(localContextList: List<Context>) {
    for(context in localContextList) {
      addContext(context)
      if (context.originalGenerator is LocalContextCodeGenerator && context.originalGenerator.isLastContext()) break
    }
  }

  private fun Context.inContextTree(): Boolean =
    this@ContextTree.myContextsTree
      .any { context ->
        when (this.originalGenerator) {
          is GlobalContextCodeGenerator -> {
            context.originalGenerator is GlobalContextCodeGenerator<*> && this.component == context.component
          }
          is LocalContextCodeGenerator -> {
            context.originalGenerator is LocalContextCodeGenerator<*> && this.component == context.component
          }
          else -> throw UnsupportedOperationException("Error: Unidentified context generator type!")
        }
      }

  private fun removeLastContext() {
    if (myContextsTree.isEmpty()) throw Exception("Error: unable to remove context from empty context tree")
    if (myContextsTree.size == 1) {
      myContextsTree.clear()
      lastContext?.originalGenerator?.closeContext()
      lastContext = null
    }
    else {
      myContextsTree.removeAt(myContextsTree.lastIndex)
      lastContext?.originalGenerator?.closeContext()
      lastContext = myContextsTree.elementAt(myContextsTree.lastIndex)
    }
    writeFun("}")
  }

  private fun Context.isAlive(inputEvent: InputEvent): Boolean {
    when (this.originalGenerator) {
      is GlobalContextCodeGenerator -> {
        return (this.component.isShowing && this.component.isEnabled)
      }
      is LocalContextCodeGenerator -> {
        if (!this.component.isEnabled || !this.component.isShowing) return false
        when(inputEvent){
          is MouseEvent -> {
            val locationOnScreen = this.component.locationOnScreen
            val visibleRect = (this.component as JComponent).visibleRect
            visibleRect.location = locationOnScreen + visibleRect.location
            return (visibleRect.contains(inputEvent.locationOnScreen))
          }
          is KeyEvent -> {
            val focusOwner = FocusManagerImpl.getGlobalInstance().focusOwner
            return (this.component == focusOwner || this.component.hasAsChild(focusOwner))
          }
          else -> {
            return false;
          }
        }
      }
      else -> throw UnsupportedOperationException("Error: Unidentified context generator type!")
    }
  }

  private operator fun Point.plus(point: Point): Point {
    return Point(this.x + point.x, this.y + point.y)
  }

  private fun Component.hasAsChild(possibleChildComponent: Component): Boolean {
    var parent: Component? = possibleChildComponent.parent
    while (parent != null) {
      if (parent == this) return true
      parent = parent.parent
    }
    return false
  }
}


