// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.customFrameDecorations.header

import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettingsListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.impl.IdeMenuBar
import com.intellij.openapi.wm.impl.customFrameDecorations.header.titleLabel.CustomDecorationPath
import com.intellij.ui.awt.RelativeRectangle
import net.miginfocom.swing.MigLayout
import java.awt.Frame
import java.awt.Rectangle
import java.util.*
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.event.ChangeListener

class MainFrameHeader(frame: JFrame) : FrameHeader(frame){
  private val mySelectedEditorFilePath: CustomDecorationPath
  private val myIdeMenu: IdeMenuBar
  private val menuHolder: JComponent
  private var changeListener: ChangeListener

  private val mainMenuUpdater: UISettingsListener

  private var disposable: Disposable? = null

  init {
    layout = MigLayout("novisualpadding, fillx, ins 0, gap 0, top, hidemode 2", "$H_GAP[pref!][][grow][pref!]")
    add(productIcon)

    myIdeMenu = CustomHeaderMenuBar()

    changeListener = ChangeListener {
      updateCustomDecorationHitTestSpots()
    }

    mySelectedEditorFilePath = CustomDecorationPath(frame) {updateCustomDecorationHitTestSpots()}

    menuHolder = JPanel(MigLayout("filly, ins 0, novisualpadding, hidemode 2", "$H_GAP[pref!]${GAP_AFTER_MENU - H_GAP}"))
    menuHolder.isOpaque = false
    menuHolder.add(myIdeMenu, "wmin 0, wmax pref, top, growy")

    add(menuHolder, "wmin 0, top, growy, pushx")
    add(mySelectedEditorFilePath.getView(), "left, growx, gapafter $H_GAP, gapbottom 1, gapbefore $H_GAP")
    add(buttonPanes.getView(), "top, wmin pref")

    setCustomFrameTopBorder({ myState != Frame.MAXIMIZED_VERT && myState != Frame.MAXIMIZED_BOTH }, {true})

    mainMenuUpdater = UISettingsListener {
      menuHolder.isVisible = UISettings.instance.showMainMenu
      SwingUtilities.invokeLater { updateCustomDecorationHitTestSpots() }
    }

    menuHolder.isVisible = UISettings.instance.showMainMenu
  }

  fun setProject(project: Project) {
    mySelectedEditorFilePath.project = project
  }

  override fun updateActive() {
    super.updateActive()
    mySelectedEditorFilePath.setActive(myActive)
  }

  override fun installListeners() {
    myIdeMenu.selectionModel.addChangeListener(changeListener)
    val disp = Disposer.newDisposable()
    Disposer.register(ApplicationManager.getApplication(), disp)

    ApplicationManager.getApplication().messageBus.connect(disp).subscribe(UISettingsListener.TOPIC, mainMenuUpdater)
    mainMenuUpdater.uiSettingsChanged(UISettings.instance)
    disposable = disp

    super.installListeners()
  }

  override fun uninstallListeners() {
    myIdeMenu.selectionModel.removeChangeListener(changeListener)
    disposable?.let {
      if (!Disposer.isDisposed(it))
        Disposer.dispose(it)
      disposable = null
    }

    super.uninstallListeners()
  }

  override fun getHitTestSpots(): ArrayList<RelativeRectangle> {
    val hitTestSpots = super.getHitTestSpots()

    if(menuHolder.isVisible) {
      val menuRect = Rectangle(menuHolder.size)

      val state = frame.extendedState
      if (state != Frame.MAXIMIZED_VERT && state != Frame.MAXIMIZED_BOTH) {
        val topGap = Math.round((menuRect.height / 3).toFloat())
        menuRect.y += topGap
        menuRect.height -= topGap
      }
      hitTestSpots.add(RelativeRectangle(menuHolder, menuRect))
    }

    hitTestSpots.addAll(mySelectedEditorFilePath.getListenerBounds())

    return hitTestSpots
  }
}