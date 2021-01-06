// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl

import com.intellij.ide.SearchTopHitProvider
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindowManager.Companion.getInstance
import com.intellij.openapi.wm.impl.ToolwindowSidebarPositionProvider.Companion.isRightPosition
import com.intellij.ui.components.OnOffButton
import com.intellij.util.ui.GridBag
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.NotNull
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.Box
import javax.swing.JLabel
import javax.swing.JPanel

class IdeLeftToolbar(private val parentDisposable: @NotNull Disposable) : JPanel() {
  lateinit var pane: ToolWindowsPane
  private val squareButtonPane: JPanel
  private val extendedPane: JPanel
  private val moreButton: MoreSquareStripeButton
  private val extendedBag: GridBag
  private val horizontalGlue = Box.createHorizontalGlue()

  init {
    initSideBar()

    extendedPane = initExtendedPane()
    extendedBag = GridBag()

    addExtendedPositionListener(extendedPane)
    add(extendedPane, getExtendedPosition())

    squareButtonPane = initMainPane()
    add(squareButtonPane, BorderLayout.CENTER)

    moreButton = MoreSquareStripeButton(this)
  }

  private fun addExtendedPositionListener(extendedPane: JPanel) {
    val listener = Runnable {
      remove(extendedPane)
      add(extendedPane, getExtendedPosition())
      revalidate()
    }
    SearchTopHitProvider.EP_NAME.findExtension(ToolwindowSidebarPositionProvider::class.java)?.let {
      it.addUpdateListener(listener)
      Disposer.register(parentDisposable, { it.removeUpdateListener(listener) })
    }
  }

  private fun initSideBar() {
    layout = BorderLayout()
    border = JBUI.Borders.empty()
    isOpaque = true
  }

  fun addStripeButton(project: Project, button: StripeButton) {
    if (PropertiesComponent.getInstance(project).getValues(STICKY_TW) == null) {
      ToolWindowSidebarProvider.getInstance().defaultToolwindows(project).forEach {
        saveTWid(project, it)
      }
    }

    if (isTWSticky(project, button)) {
      rebuildOrderedSquareButtons(project)
    }

    addButtonOnExtendedPane(project, button)
  }

  private fun addButtonOnExtendedPane(project: Project, button: StripeButton) {
    extendedBag.nextLine()
    extendedPane.apply {
      remove(horizontalGlue)
      add(SquareStripeButton(button), extendedBag.next())
      add(JLabel(button.text).apply { border = JBUI.Borders.emptyRight(50) }, extendedBag.next().anchor(GridBagConstraints.WEST))
      add(createOnOffButton(project, button), extendedBag.next().coverLine())
      add(horizontalGlue, extendedBag.nextLine().next().weighty(1.0).fillCell().coverLine())
    }
  }

  private fun createOnOffButton(project: Project, button: StripeButton) =
    OnOffButton().also {
      it.model.isSelected = isTWSticky(project, button)

      it.addActionListener(object : AbstractAction() {
        override fun actionPerformed(e: ActionEvent) {
          if (it.isSelected) {
            addStickySquareStripeButton(project, button)
          }
          else {
            removeSquareStripeButton(project, button)
          }
          squareButtonPane.revalidate()
        }
      })
    }

  private fun rebuildOrderedSquareButtons(project: Project) {
    squareButtonPane.removeAll()
    PropertiesComponent.getInstance(project).getValues(STICKY_TW).orEmpty().forEach { sticky ->
      val toolWindow = getInstance(project).getToolWindow(sticky) as ToolWindowImpl? ?: return@forEach
      squareButtonPane.add(SquareStripeButton(StripeButton(pane, toolWindow).also { it.updatePresentation() }))
    }
    squareButtonPane.add(moreButton)
  }

  private fun addStickySquareStripeButton(project: Project, button: StripeButton) {
    saveTWid(project, button.toolWindow.id)
    rebuildOrderedSquareButtons(project)
  }

  private fun removeSquareStripeButton(project: Project, button: StripeButton) {
    unsetTWid(project, button)
    rebuildOrderedSquareButtons(project)
  }

  fun openExtendedToolwindowPane(show: Boolean) {
    extendedPane.isVisible = show
  }

  fun isExtendedToolwindowPaneShown() = extendedPane.isVisible

  companion object {
    private const val STICKY_TW = "STICKY_TW"

    private fun saveTWid(project: Project, id: String) {
      val stickies = PropertiesComponent.getInstance(project).getValues(STICKY_TW).orEmpty() as Array<String>
      if (!stickies.contains(id)) {
        PropertiesComponent.getInstance(project).setValues(STICKY_TW, stickies.plus(id))
      }
    }

    private fun isTWSticky(project: Project, button: StripeButton) =
      PropertiesComponent.getInstance(project).getValues(STICKY_TW)?.contains(button.toolWindow.id) ?: false

    private fun unsetTWid(project: Project, button: StripeButton) {
      PropertiesComponent.getInstance(project).setValues(
        STICKY_TW,
        (PropertiesComponent.getInstance(project).getValues(STICKY_TW).orEmpty()).toMutableList().apply {
          remove(button.toolWindow.id)
        }.toTypedArray().ifEmpty { null })
    }

    private fun initMainPane() = JPanel(VerticalFlowLayout(0, 0))
      .apply {
        border = JBUI.Borders.customLine(JBUI.CurrentTheme.ToolWindow.borderColor(), 1,
                                         if (isRightPosition()) 1 else 0, 0, if (isRightPosition()) 0 else 1)
      }

    private fun initExtendedPane() = JPanel()
      .apply {
        layout = GridBagLayout()
        isVisible = false
        background = JBUI.CurrentTheme.BigPopup.searchFieldBackground()
        border = JBUI.Borders.customLine(JBUI.CurrentTheme.ToolWindow.borderColor(), 1,
                                         if (isRightPosition()) 1 else 0, 0, if (isRightPosition()) 0 else 1)
      }

    @JvmStatic fun getMainPosition() = if (isRightPosition()) BorderLayout.EAST else BorderLayout.WEST
    @JvmStatic fun getExtendedPosition() = if (isRightPosition()) BorderLayout.WEST else BorderLayout.EAST
  }
}