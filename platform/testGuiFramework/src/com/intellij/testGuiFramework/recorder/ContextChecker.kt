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

import com.intellij.openapi.wm.impl.IdeFrameImpl
import com.intellij.openapi.wm.impl.ToolWindowImpl
import com.intellij.openapi.wm.impl.ToolWindowManagerImpl
import com.intellij.openapi.wm.impl.WindowManagerImpl
import com.intellij.testGuiFramework.generators.*
import java.awt.Component
import java.awt.Point
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

  private fun getGlobalApplicableContext(component: Component, me: MouseEvent, cp: Point): Context? {
    val applicableContextGenerator = globalContextGenerators.filter { generator -> generator.accept(component) }.sortedByDescending(
      ContextCodeGenerator<*>::priority).firstOrNull() ?: return null
    return applicableContextGenerator.buildContext(component, me, cp)
  }

  private fun getLocalApplicableContext(component: Component, me: MouseEvent, cp: Point): List<Context> =
    localContextGenerators.filter { generator -> generator.accept(component) }
      .sortedBy(ContextCodeGenerator<*>::priority)
      .map { applicableGenerator -> applicableGenerator.buildContext(component, me, cp) }

  private fun Component.containsLocationOnScreen(locationOnScreen: Point): Boolean {
    val rectangle = this.bounds
    rectangle.location = this.locationOnScreen
    return rectangle.contains(locationOnScreen)
  }

  private fun toolwindowCheck(component: Component, me: MouseEvent, cp: Point) {
    if (WindowManagerImpl.getInstance().findVisibleFrame() !is IdeFrameImpl) return
    val ideFrame = WindowManagerImpl.getInstance().findVisibleFrame() as IdeFrameImpl
    if (ideFrame.project == null) return
    val toolWindowManager = ToolWindowManagerImpl.getInstance(ideFrame.project!!)
    val visibleToolWindows = toolWindowManager.toolWindowIds
      .map { toolWindowId -> toolWindowManager.getToolWindow(toolWindowId) }
      .filter { toolwindow -> toolwindow.isVisible }
    println("Active toolwindows: $visibleToolWindows")
    val toolwindow: ToolWindowImpl = visibleToolWindows
                                       .filterIsInstance<ToolWindowImpl>()
                                       .find { it.component.containsLocationOnScreen(me.locationOnScreen) } ?: return
    println("Toolwindow with id(\"${(toolwindow.id)}\" hosts a cursor)")
  }


  fun getContextDepth(): Int = contextTree.getSize()

  fun clearContext() {
    contextTree.clear()
  }

  private fun List<*>?.isNullOrEmpty(): Boolean = this == null || this.isEmpty()

  fun checkContext(component: Component, me: MouseEvent, cp: Point) {
//        toolwindowCheck(component, me, cp)
    val globalContext = getGlobalApplicableContext(component, me, cp)
    val localContextList: List<Context> = getLocalApplicableContext(component, me, cp)
    contextTree.checkAliveContexts(me)
    if (globalContext != null) contextTree.addContext(globalContext, me)
    if (!localContextList.isNullOrEmpty()) contextTree.addContexts(localContextList, me)
  }


  private class ContextTree(val writeFun: (String) -> Unit) {

    private val myContextsTree: ArrayList<Context> = ArrayList()
    private var lastContext: Context? = null

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

    private fun getLastGlobalContext(): Context? = myContextsTree.filter { context -> context.originalGenerator is GlobalContextCodeGenerator }.lastOrNull()

    private fun removeLastContext() {
      if (myContextsTree.isEmpty()) throw Exception("Error: unable to remove context from empty context tree")
      if (myContextsTree.size == 1) {
        myContextsTree.clear()
        lastContext!!.originalGenerator.closeContext()
        lastContext = null
      }
      else {
        myContextsTree.removeAt(myContextsTree.lastIndex)
        lastContext!!.originalGenerator.closeContext()
        lastContext = myContextsTree.elementAt(myContextsTree.lastIndex)
      }
      writeFun("}")
    }

    fun checkAliveContexts(me: MouseEvent) {
      for (i in (0..myContextsTree.lastIndex)) {
        if (!myContextsTree.get(i).isAlive(me)) {
          // from i to myContextsTree.lastIndex contexts should be dropped
          while (myContextsTree.lastIndex >= i) removeLastContext()
          break
        }
      }
    }

    private fun Context.isAlive(me: MouseEvent): Boolean {
      when (this.originalGenerator) {
        is GlobalContextCodeGenerator -> {
          return (this.component.isShowing && this.component.isEnabled)
        }
        is LocalContextCodeGenerator -> {
          if (!(this.component.isEnabled && this.component.isShowing)) return false
          val locationOnScreen = this.component.locationOnScreen
          val visRect = (this.component as JComponent).visibleRect
          visRect.location = locationOnScreen
          return (visRect.contains(me.locationOnScreen))
        }
        else -> throw UnsupportedOperationException("Error: Unidentified context generator type!")
      }
    }

    fun addContext(context: Context, me: MouseEvent) {
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

    fun addContexts(localContextList: List<Context>, me: MouseEvent) {
      localContextList.forEach { addContext(it, me) }
    }

  }

}


