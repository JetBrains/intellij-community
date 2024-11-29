// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.service
import com.intellij.ui.tabs.impl.TabLabel
import com.intellij.util.concurrency.annotations.RequiresEdt
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import java.awt.Rectangle
import javax.swing.*
import kotlin.time.Duration.Companion.milliseconds

@ApiStatus.Internal
class DeferredIconRepaintScheduler {
  private var job: Job? = null
  private val queue = ObjectLinkedOpenHashSet<RepaintSchedulerRequest>()

  @RequiresEdt
  fun createRepaintRequest(component: Component?, x: Int, y: Int): RepaintRequest {
    val target = getTarget(component)
    val paintingParent = SwingUtilities.getAncestorOfClass(PaintingParent::class.java, component)
    val paintingParentRec = if (paintingParent == null) null else (paintingParent as PaintingParent).getChildRec(component!!)
    return RepaintRequest(component = component,
                          x = x,
                          y = y,
                          target = target,
                          paintingParent = paintingParent,
                          paintingParentRec = paintingParentRec)
  }

  @RequiresEdt
  fun scheduleRepaint(request: RepaintRequest, iconWidth: Int, iconHeight: Int, alwaysSchedule: Boolean) {
    val actualTarget = request.getActualTarget() ?: return
    val component = request.component
    if (!alwaysSchedule && component == actualTarget) {
      component.repaint(request.x, request.y, iconWidth, iconHeight)
    }
    else {
      job?.cancel()
      queue.add(RepaintSchedulerRequest(actualTarget, request.paintingParentRec))
      job = schedule()
    }
  }

  private fun schedule(): Job {
    val modality = ModalityState.current()
    return service<IconCalculatingService>().coroutineScope.launch {
      delay(50.milliseconds)
      withContext(Dispatchers.EDT + modality.asContextElement()) {
        while (!queue.isEmpty()) {
          ensureActive()
          val request = queue.removeFirst()
          val rectangle = request.rectangle
          if (rectangle == null) {
            request.component.repaint()
          }
          else {
            request.component.repaint(rectangle.x, rectangle.y, rectangle.width, rectangle.height)
          }
        }
      }
    }
  }

  private fun getTarget(c: Component?): Component? {
    @Suppress("SpellCheckingInspection")
    return SwingUtilities.getAncestorOfClass(JList::class.java, c)
           // check table first to process com.intellij.ui.treeStructure.treetable.TreeTable correctly
           ?: SwingUtilities.getAncestorOfClass(JTable::class.java, c)
           ?: SwingUtilities.getAncestorOfClass(JTree::class.java, c)
           ?: SwingUtilities.getAncestorOfClass(JComboBox::class.java, c)
           ?: SwingUtilities.getAncestorOfClass(TabLabel::class.java, c)
           ?: c
  }
}

private data class RepaintSchedulerRequest(@JvmField val component: Component, @JvmField val rectangle: Rectangle?)

@ApiStatus.Internal
data class RepaintRequest(
  @JvmField internal val component: Component?,
  @JvmField internal val x: Int,
  @JvmField internal val y: Int,
  @JvmField internal val target: Component?,
  private val paintingParent: Component?,
  @JvmField internal val paintingParentRec: Rectangle?
) {
  internal fun getActualTarget(): Component? {
    if (target == null) {
      return null
    }
    if (SwingUtilities.getWindowAncestor(target) != null) {
      return target
    }
    return paintingParent?.takeIf { SwingUtilities.getWindowAncestor(paintingParent) != null }
  }
}