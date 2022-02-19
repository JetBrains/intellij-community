// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.customFrameDecorations.header

import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettingsListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.impl.IdeMenuBar
import com.intellij.openapi.wm.impl.customFrameDecorations.header.title.CustomHeaderTitle
import com.intellij.ui.awt.RelativeRectangle
import com.intellij.util.ui.JBUI
import com.jetbrains.CustomWindowDecoration.*
import net.miginfocom.swing.MigLayout
import java.awt.Frame
import java.awt.Rectangle
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.event.ChangeListener
import kotlin.math.roundToInt

internal class MenuFrameHeader(frame: JFrame, val headerTitle: CustomHeaderTitle, val myIdeMenu: IdeMenuBar) : FrameHeader(frame), MainFrameCustomHeader {
  private val menuHolder: JComponent
  private var changeListener: ChangeListener

  private val mainMenuUpdater: UISettingsListener

  private var disposable: Disposable? = null

  init {
    layout = MigLayout("novisualpadding, fillx, ins 0, gap 0, top, hidemode 2", "[pref!][][grow][pref!]")
    val empty = JBUI.Borders.empty(V, H, V, 0)

    productIcon.border = empty
    add(productIcon)

    changeListener = ChangeListener {
      updateCustomDecorationHitTestSpots()
    }

    headerTitle.onBoundsChanged = { windowStateChanged() }

    menuHolder = JPanel(MigLayout("filly, ins 0, novisualpadding, hidemode 3", "[pref!]${JBUI.scale(10)}"))
    menuHolder.border = JBUI.Borders.empty(0, H - 1, 0, 0)
    menuHolder.isOpaque = false
    menuHolder.add(myIdeMenu, "wmin 0, wmax pref, top, growy")

    add(menuHolder, "wmin 0, top, growy, pushx")
    val view = headerTitle.view.apply {
      border = empty
    }

    add(view, "left, growx, gapbottom 1")
    add(buttonPanes.getView(), "top, wmin pref")

    setCustomFrameTopBorder({ myState != Frame.MAXIMIZED_VERT && myState != Frame.MAXIMIZED_BOTH }, {true})

    mainMenuUpdater = UISettingsListener {
      menuHolder.isVisible = UISettings.getInstance().showMainMenu
      SwingUtilities.invokeLater { updateCustomDecorationHitTestSpots() }
    }

    menuHolder.isVisible = UISettings.getInstance().showMainMenu
  }

  override fun updateMenuActions(forceRebuild: Boolean) {
    myIdeMenu.updateMenuActions(forceRebuild)
  }

  override fun getComponent(): JComponent = this

  override fun updateActive() {
    super.updateActive()
    headerTitle.setActive(myActive)
  }

  override fun installListeners() {
    myIdeMenu.selectionModel.addChangeListener(changeListener)
    val disp = Disposer.newDisposable()
    Disposer.register(ApplicationManager.getApplication(), disp)

    ApplicationManager.getApplication().messageBus.connect(disp).subscribe(UISettingsListener.TOPIC, mainMenuUpdater)
    mainMenuUpdater.uiSettingsChanged(UISettings.getInstance())
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

  override fun getHitTestSpots(): List<Pair<RelativeRectangle, Int>> {
    val hitTestSpots = super.getHitTestSpots().toMutableList()
    if (menuHolder.isVisible) {
      val menuRect = Rectangle(menuHolder.size)

      val state = frame.extendedState
      if (state != Frame.MAXIMIZED_VERT && state != Frame.MAXIMIZED_BOTH) {
        val topGap = (menuRect.height / 3).toFloat().roundToInt()
        menuRect.y += topGap
        menuRect.height -= topGap
      }
      hitTestSpots.add(Pair(RelativeRectangle(menuHolder, menuRect), MENU_BAR))
    }
    hitTestSpots.addAll(headerTitle.getBoundList().map { Pair(it, OTHER_HIT_SPOT) })
    return hitTestSpots
  }
}