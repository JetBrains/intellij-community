// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.welcomeScreen.learnIde

import com.intellij.icons.AllIcons.Ide.External_link_arrow
import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.HelpTopicsAction
import com.intellij.ide.actions.JetBrainsTvAction
import com.intellij.ide.actions.OnlineDocAction
import com.intellij.ide.actions.WhatsNewAction
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.wm.InteractiveCourseFactory
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenUIManager
import com.intellij.openapi.wm.impl.welcomeScreen.learnIde.LearnIdeContentColorsAndFonts.HeaderColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.Rectangle
import javax.swing.*
import javax.swing.plaf.ComponentUI

class LearnIdeContentPanel(private val parentDisposable: Disposable) : JPanel() {

  //unscalable insets
  private val unscalable24px = 24

  private val interactiveCoursesPanel: JPanel = JPanel()
  private val helpAndResourcesPanel: JPanel = JPanel()
  private val contentPanel: JPanel = JPanel()
  private val myScrollPane: JBScrollPane = JBScrollPane(contentPanel, ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS,
                                                        ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER).apply { border = JBUI.Borders.empty() }
  private val interactiveCoursesHeader: JTextPane = HeightLimitedPane(IdeBundle.message("welcome.screen.learnIde.interactive.courses.text"),
                                                                      5, HeaderColor, true)
  private val helpAndResourcesHeader: JTextPane = HeightLimitedPane(IdeBundle.message("welcome.screen.learnIde.help.and.resources.text"),
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
    initHelpAndResourcePanel()

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

  private fun initHelpAndResourcePanel() {
    helpAndResourcesPanel.apply {
      layout = BoxLayout(this, BoxLayout.PAGE_AXIS)
      isOpaque = false
      add(helpAndResourcesHeader)
      add(rigid(0, 1))
      addHelpActions()
    }
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


  private fun addHelpActions() {
    val whatsNewAction = WhatsNewAction()
    if (emptyWelcomeScreenEventFromAction(whatsNewAction).presentation.isEnabled) {
      helpAndResourcesPanel.add(linkLabelByAction(WhatsNewAction()))
      helpAndResourcesPanel.add(rigid(1, 16))
    }
    val helpActions = ActionManager.getInstance().getAction(IdeActions.GROUP_WELCOME_SCREEN_LEARN_IDE) as ActionGroup
    val anActionEvent = emptyWelcomeScreenEventFromAction(helpActions)
    helpActions.getChildren(anActionEvent).forEach {
      if (setOf<String>(HelpTopicsAction::class.java.simpleName, OnlineDocAction::class.java.simpleName,
                        JetBrainsTvAction::class.java.simpleName).any { simpleName -> simpleName == it.javaClass.simpleName }) {
        helpAndResourcesPanel.add(linkLabelByAction(it).wrapWithUrlPanel())
      }
      else {
        helpAndResourcesPanel.add(linkLabelByAction(it))

      }
      helpAndResourcesPanel.add(rigid(1, 6))
    }
  }

  private fun LinkLabel<Any>.wrapWithUrlPanel(): JPanel {
    val jPanel = JPanel()
    jPanel.isOpaque = false
    jPanel.layout = BoxLayout(jPanel, BoxLayout.LINE_AXIS)
    jPanel.add(this, BorderLayout.CENTER)
    jPanel.add(JLabel(External_link_arrow), BorderLayout.EAST)
    jPanel.maximumSize = jPanel.preferredSize
    jPanel.alignmentX = LEFT_ALIGNMENT
    return jPanel
  }

  private fun emptyWelcomeScreenEventFromAction(action: AnAction) =
    AnActionEvent.createFromAnAction(action, null, ActionPlaces.WELCOME_SCREEN, DataContext.EMPTY_CONTEXT)

  private fun linkLabelByAction(it: AnAction): LinkLabel<Any> {
    return LinkLabel<Any>(it.templateText, null).apply {
      alignmentX = LEFT_ALIGNMENT
      setListener({ _, _ -> performActionOnWelcomeScreen(it) }, null)
    }
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

  private fun performActionOnWelcomeScreen(action: AnAction) {
    val anActionEvent = AnActionEvent.createFromAnAction(action, null, ActionPlaces.WELCOME_SCREEN, DataContext.EMPTY_CONTEXT)
    ActionUtil.performActionDumbAwareWithCallbacks(action, anActionEvent)
  }

}