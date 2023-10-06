// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.impl.presentationAssistant

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.scale.JBUIScale
import java.awt.Dimension
import java.awt.Point
import java.awt.Rectangle

internal class ActionInfoPopupGroup(project: Project, textFragments: List<TextData>, showAnimated: Boolean) {
  val panels = textFragments.mapIndexed { index, fragment -> ActionInfoPanel(index, project, listOf(fragment), showAnimated, this::computeLocation) }
  private val configuration = PresentationAssistant.INSTANCE.configuration
  val isShown: Boolean get() = panels.all { it.phase == ActionInfoPanel.Phase.SHOWN }

  init {
    panels.forEach { it.presentPopup(project) }
  }

  fun updateText(project: Project, textFragments: List<TextData>) {
    panels.forEach { it.updateText(project, listOf(textFragments[it.index])) }
    panels.forEach { it.updateHintBounds(project) }
  }

  fun close() {
    panels.forEach { it.close() }
  }

  fun canBeReused(size: Int): Boolean = size == panels.size && panels.all { it.canBeReused() }

  private fun computeLocation(project: Project, index: Int?): RelativePoint {
    val preferredSizes = panels.map { it.preferredSize }
    val gap = JBUIScale.scale(12)
    val popupGroupSize: Dimension = if (panels.isNotEmpty()) {
      val totalWidth = preferredSizes.map { it.width }.reduce { total, width -> total + width + gap } - gap
      Dimension(totalWidth, preferredSizes.first().height)
    }
    else Dimension()

    val ideFrame = WindowManager.getInstance().getIdeFrame(project)!!
    val statusBarHeight = ideFrame.statusBar?.component?.height ?: 0
    val visibleRect = ideFrame.component.visibleRect

    val x = when (configuration.horizontalAlignment) {
      0 -> visibleRect.x + configuration.margin
      1 -> visibleRect.x + (visibleRect.width - popupGroupSize.width) / 2
      else -> visibleRect.x + visibleRect.width - popupGroupSize.width - configuration.margin
    } + (index?.takeIf {
      0 < index && index < panels.size
    }?.let {
      // Calculate X for particular popup
      (0..<index).map { preferredSizes[it].width }.reduce { total, width ->
        total + width
      } + gap * index
    } ?: 0)

    val y = when (configuration.verticalAlignment) {
      0 -> visibleRect.y + configuration.margin
      else -> visibleRect.y + visibleRect.height - popupGroupSize.height - statusBarHeight - configuration.margin
    }

    if (index != null) println("ayay calculated for $index: ${Rectangle(Point(x, y), preferredSizes[index])}")

    return RelativePoint(ideFrame.component, Point(x, y))
  }
}