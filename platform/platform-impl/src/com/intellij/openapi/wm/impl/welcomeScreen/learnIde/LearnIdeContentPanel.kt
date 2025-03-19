// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.welcomeScreen.learnIde

import com.intellij.ide.IdeBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.wm.InteractiveCourseFactory
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenUIManager
import com.intellij.openapi.wm.impl.welcomeScreen.learnIde.LearnIdeContentColorsAndFonts.HeaderColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.Rectangle
import javax.swing.*
import javax.swing.plaf.ComponentUI

@ApiStatus.Internal
class LearnIdeContentPanel(private val parentDisposable: Disposable) : JPanel() {

  //unscalable insets
  private val unscalable24px = 24

  private val interactiveCoursesPanel: JPanel = JPanel()
  private val helpAndResourcesPanel: HelpAndResourcesPanel = HelpAndResourcesPanel()
  private val contentPanel: JPanel = JPanel()
  private val myScrollPane: JBScrollPane = JBScrollPane(contentPanel, ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS,
                                                        ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER).apply { border = JBUI.Borders.empty() }
  private val interactiveCoursesHeader: JTextPane = HeightLimitedPane(IdeBundle.message("welcome.screen.learnIde.interactive.courses.text"),
                                                                      5, HeaderColor, true)

  init {
    layout = BorderLayout()
    isFocusable = false
    isOpaque = true
    background = WelcomeScreenUIManager.getProjectsBackground()

    contentPanel.apply {
      layout = BorderLayout()
      border = JBUI.Borders.empty(unscalable24px)
      background = WelcomeScreenUIManager.getProjectsBackground()
    }

    val interactiveCoursesExtensions = InteractiveCourseFactory.INTERACTIVE_COURSE_FACTORY_EP.extensions
    initInteractiveCoursesPanel(interactiveCoursesExtensions)

    if (interactiveCoursesExtensions.none { it.isActive }) {
      contentPanel.add(helpAndResourcesPanel, BorderLayout.CENTER)
    }
    else {
      contentPanel.add(helpAndResourcesPanel, BorderLayout.SOUTH)
    }

    //set LearnPanel UI
    add(myScrollPane, BorderLayout.CENTER)
    contentPanel.bounds = Rectangle(contentPanel.location, contentPanel.preferredSize)
    revalidate()
    repaint()
  }

  override fun updateUI() {
    super.updateUI()
    if (parent != null) reInitHelpAndResourcePanel()
  }

  private fun reInitHelpAndResourcePanel() {
    helpAndResourcesPanel.removeAll()
    helpAndResourcesPanel.initPanel()
  }

  private fun initInteractiveCoursesPanel(interactiveCoursesExtensions: Array<InteractiveCourseFactory>) {
    updateInteractiveCoursesPanel(interactiveCoursesExtensions)
    InteractiveCourseFactory.INTERACTIVE_COURSE_FACTORY_EP.addExtensionPointListener(
      object : ExtensionPointListener<InteractiveCourseFactory> {
        override fun extensionAdded(extension: InteractiveCourseFactory, pluginDescriptor: PluginDescriptor) {
          updateInteractiveCoursesPanel(interactiveCoursesExtensions)
        }

        override fun extensionRemoved(extension: InteractiveCourseFactory, pluginDescriptor: PluginDescriptor) {
          updateInteractiveCoursesPanel(interactiveCoursesExtensions)
        }
      }, parentDisposable)
  }

  private fun updateInteractiveCoursesPanel(interactiveCoursesExtensions: Array<InteractiveCourseFactory>) {
    //clear before
    interactiveCoursesPanel.removeAll()
    contentPanel.remove(interactiveCoursesPanel)

    interactiveCoursesPanel.layout = BoxLayout(interactiveCoursesPanel, BoxLayout.LINE_AXIS)
    interactiveCoursesPanel.isOpaque = false

    val componentsList = interactiveCoursesExtensions.filter { it.isActive }.map { it.getInteractiveCourseComponent() }
    if (componentsList.isNotEmpty()) {
      for (interactiveCourse in componentsList) {
        interactiveCoursesPanel.add(interactiveCourse)
        interactiveCoursesPanel.add((rigid(12, 6)))
      }
      contentPanel.add(interactiveCoursesHeader, BorderLayout.NORTH)
      contentPanel.add(interactiveCoursesPanel, BorderLayout.CENTER)
    }
    revalidate()
    repaint()
  }


  private fun rigid(_width: Int, _height: Int): Component {
    val d = Dimension(JBUI.scale(_width), JBUI.scale(_height))
    return object: Box.Filler(d, d, d) {
      init {
        alignmentX = LEFT_ALIGNMENT
      }

      override fun updateUI() {
        super.updateUI()
        val newD = Dimension(JBUI.scale(_width), JBUI.scale(_height))
        minimumSize = newD
        preferredSize = newD
        maximumSize = newD
      }

      override fun setUI(newUI: ComponentUI?) {
        super.setUI(newUI)
      }
    }
  }
}