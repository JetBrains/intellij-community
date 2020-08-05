// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.customFrameDecorations.header

import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettingsListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.impl.IdeMenuBar
import com.intellij.ui.awt.RelativeRectangle
import com.intellij.util.ui.JBUI
import net.miginfocom.swing.MigLayout
import java.awt.Frame
import java.awt.Rectangle
import java.util.*
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.event.ChangeListener

class FrameWithMenuHeader(frame: JFrame, val myIdeMenu: IdeMenuBar) : FrameHeader(frame) {
  private val menuHolder: JComponent
  private var changeListener: ChangeListener
  private var disposable: Disposable? = null

  private val mainMenuUpdater: UISettingsListener

  init {
    layout = MigLayout("novisualpadding, fillx, ins 0, gap 0, top, hidemode 2", "[pref!][]push[pref!]")
    val empty = JBUI.Borders.empty(V, H, V, 0)

    productIcon.border = empty
    add(productIcon)

    changeListener = ChangeListener {
      updateCustomDecorationHitTestSpots()
    }

    menuHolder = JPanel(MigLayout("filly, ins 0, novisualpadding, hidemode 3", "[pref!]${JBUI.scale(10)}"))
    menuHolder.border = JBUI.Borders.empty(0, H - 1, 0, 0)
    menuHolder.isOpaque = false
    menuHolder.add(myIdeMenu, "wmin 0, wmax pref, top, growy")

    add(menuHolder, "wmin 0, top, growy, pushx")

    add(buttonPanes.getView(), "top, wmin pref")

    setCustomFrameTopBorder({ myState != Frame.MAXIMIZED_VERT && myState != Frame.MAXIMIZED_BOTH }, {true})

    mainMenuUpdater = UISettingsListener {
      menuHolder.isVisible = UISettings.instance.showMainMenu
      SwingUtilities.invokeLater { updateCustomDecorationHitTestSpots() }
    }

    menuHolder.isVisible = UISettings.instance.showMainMenu
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

    return hitTestSpots
  }
}