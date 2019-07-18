// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.customFrameDecorations.header

import com.intellij.ide.DataManager
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettingsListener
import com.intellij.openapi.actionSystem.ex.ActionManagerEx
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.impl.IdeMenuBar
import com.intellij.openapi.wm.impl.customFrameDecorations.header.titleLabel.CustomDecorationPath
import com.intellij.ui.awt.RelativeRectangle
import com.intellij.util.ui.JBUI
import net.miginfocom.swing.MigLayout
import java.awt.Frame
import java.awt.Rectangle
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.util.*
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.border.Border
import javax.swing.event.ChangeListener

class MainFrameHeader(frame: JFrame) : FrameHeader(frame){
  private val mySelectedEditorFilePath: CustomDecorationPath
  private val myIdeMenu: IdeMenuBar
  private var changeListener: ChangeListener

  init {
    layout = MigLayout("novisualpadding, fillx, ins 0, gap 0, top", "$H_GAP[pref!]$H_GAP[][pref!]")
    add(productIcon)

    val frameDisposer = Disposer.newDisposable()
    Disposer.register(ApplicationManager.getApplication(), frameDisposer)
    frame.addWindowListener(object: WindowAdapter() {
      override fun windowClosed(e: WindowEvent?) {
        Disposer.dispose(frameDisposer)
      }
    })
    myIdeMenu = object : IdeMenuBar(ActionManagerEx.getInstanceEx(), DataManager.getInstance()) {
      override fun getBorder(): Border? {
        return JBUI.Borders.empty()
      }
    }

    val pane = JPanel(MigLayout("fillx, ins 0, novisualpadding", "[pref!][]"))
    pane.isOpaque = false
    setCustomFrameTopBorder({ myState != Frame.MAXIMIZED_VERT && myState != Frame.MAXIMIZED_BOTH }, {true})
    mySelectedEditorFilePath = CustomDecorationPath()
    relayoutFrameHeader(pane, true)

    val mainMenuUpdater = UISettingsListener {
      if (myIdeMenu.parent == null && UISettings.instance.showMainMenu) {
        relayoutFrameHeader(pane, true)
      }
      else if (!UISettings.instance.showMainMenu) {
        relayoutFrameHeader(pane, false)
      }
    }
    ApplicationManager.getApplication().messageBus.connect(frameDisposer).subscribe(UISettingsListener.TOPIC, mainMenuUpdater)
    mainMenuUpdater.uiSettingsChanged(UISettings.instance)


    changeListener = ChangeListener {
      updateCustomDecorationHitTestSpots()
    }
  }

  private fun relayoutFrameHeader(pane: JPanel, showMainMenu: Boolean) {
    removeAll()
    add(productIcon)
    pane.removeAll()
    if (showMainMenu) {
      pane.add(myIdeMenu, "wmin 0, wmax pref, top, hmin $MIN_HEIGHT")
    }
    pane.add(mySelectedEditorFilePath.getView(), "center, growx, wmin 0, gapbefore $H_GAP, gapafter $H_GAP, gapbottom 1")
    add(pane, "wmin 0, growx")
    add(buttonPanes.getView(), "top, wmin pref")
  }

  fun setProject(project: Project) {
    mySelectedEditorFilePath.setProject(project)
  }

  override fun updateActive() {
    super.updateActive()
    mySelectedEditorFilePath.setActive(myActive)
  }

  override fun installListeners() {
    myIdeMenu.selectionModel.addChangeListener(changeListener)
    super.installListeners()
  }

  override fun uninstallListeners() {
    myIdeMenu.selectionModel.removeChangeListener(changeListener)
    super.uninstallListeners()
  }

  override fun getHitTestSpots(): ArrayList<RelativeRectangle> {
    val hitTestSpots = super.getHitTestSpots()
    val menuRect = Rectangle(myIdeMenu.size)

    val state = frame.extendedState
    if (state != Frame.MAXIMIZED_VERT && state != Frame.MAXIMIZED_BOTH) {
        val topGap = Math.round((menuRect.height / 3).toFloat())
        menuRect.y += topGap
        menuRect.height -=topGap
    }

    hitTestSpots.add(RelativeRectangle(myIdeMenu, menuRect))
    hitTestSpots.addAll(mySelectedEditorFilePath.getListenerBounds())

    return hitTestSpots
  }
}