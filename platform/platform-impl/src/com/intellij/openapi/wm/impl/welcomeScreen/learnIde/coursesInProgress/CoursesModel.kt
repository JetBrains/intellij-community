// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen.learnIde.coursesInProgress

import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.ComponentUtil
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import java.awt.event.ComponentEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.SwingUtilities

@ApiStatus.Internal
class CoursesModel {
  private val courseCards: MutableList<CourseCardComponent> = mutableListOf()
  private var hoveredCard: CourseCardComponent? = null
  private val mouseListener: MouseAdapter = HoverMouseHandler()
  private val clickHandler: MouseAdapter = ClickMouseHandler()

  var onClick: (CourseInfo) -> Boolean = { false }

  fun addCourseCard(cardComponent: CourseCardComponent) {
    courseCards.add(cardComponent)
    cardComponent.getClickComponent().addMouseListener(mouseListener)
    addNavigationListenersRecursively(cardComponent)
    addClickListenerRecursively(cardComponent, cardComponent.actionComponent)
  }

  fun clear() {
    courseCards.clear()
    hoveredCard = null
  }

  private fun addNavigationListeners(component: Component) {
    component.addMouseListener(mouseListener)
    component.addMouseMotionListener(mouseListener)
  }

  private fun addNavigationListenersRecursively(component: Component) {
    addNavigationListeners(component)
    for (child in UIUtil.uiChildren(component)) {
      addNavigationListenersRecursively(child)
    }
  }

  private fun addClickListenerRecursively(component: Component, nonClickableComponent: JComponent) {
    component.addMouseListener(clickHandler)
    for (child in UIUtil.uiChildren(component)) {
      if (child != nonClickableComponent) {
        addClickListenerRecursively(child, nonClickableComponent)
      }
    }
  }

  private fun getCourseCard(event: ComponentEvent): CourseCardComponent? {
    return ComponentUtil.getParentOfType(CourseCardComponent::class.java, event.component)
  }


  private inner class ClickMouseHandler : MouseAdapter() {
    override fun mouseClicked(event: MouseEvent) {
      if (SwingUtilities.isLeftMouseButton(event)) {
        val cardComponent = getCourseCard(event) ?: return
        if (onClick(cardComponent.data)) {
          return
        }
        IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown {
          IdeFocusManager.getGlobalInstance().requestFocus(cardComponent as Component, true)
        }
      }
    }
  }

  private inner class HoverMouseHandler : MouseAdapter() {

    override fun mouseExited(event: MouseEvent) {
      hoveredCard?.onHoverEnded()
      hoveredCard = null
    }

    override fun mouseMoved(event: MouseEvent) {
      val cardComponent = getCourseCard(event)
      hoveredCard = cardComponent

      cardComponent?.onHover()
    }
  }

}