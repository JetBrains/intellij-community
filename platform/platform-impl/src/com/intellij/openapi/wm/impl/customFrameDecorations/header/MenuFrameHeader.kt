// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.customFrameDecorations.header

import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettingsListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.CheckedDisposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.impl.IdeMenuBar
import com.intellij.openapi.wm.impl.customFrameDecorations.header.title.CustomHeaderTitle
import com.intellij.util.ui.JBUI
import net.miginfocom.swing.MigLayout
import java.awt.Frame
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.event.ChangeListener

internal class MenuFrameHeader(frame: JFrame,
                               private val headerTitle: CustomHeaderTitle,
                               private val ideMenu: IdeMenuBar) : FrameHeader(frame), MainFrameCustomHeader {
  private val menuHolder: JComponent
  private var changeListener: ChangeListener

  private val mainMenuUpdater: UISettingsListener

  private var disposable: CheckedDisposable? = null

  init {
    layout = MigLayout("novisualpadding, fillx, ins 0, gap 0, top, hidemode 2", "[pref!][][grow][pref!]")
    val empty = JBUI.Borders.empty(V, H, V, 0)

    productIcon.border = empty
    add(productIcon)

    changeListener = ChangeListener {
      updateCustomTitleBar()
    }

    headerTitle.onBoundsChanged = { windowStateChanged() }

    menuHolder = JPanel(MigLayout("filly, ins 0, novisualpadding, hidemode 3", "[pref!]10"))
    menuHolder.border = JBUI.Borders.empty(0, H - 1, 0, 0)
    menuHolder.isOpaque = false
    menuHolder.add(ideMenu, "wmin 0, wmax pref, top, growy")

    add(menuHolder, "wmin 0, top, growy, pushx")
    val view = headerTitle.view
    view.border = empty

    add(view, "left, growx, gapbottom 1")
    buttonPanes?.let { add(it.getView(), "right, gapbottom 1") }

    setCustomFrameTopBorder({ myState != Frame.MAXIMIZED_VERT && myState != Frame.MAXIMIZED_BOTH }, {true})

    mainMenuUpdater = UISettingsListener {
      menuHolder.isVisible = UISettings.getInstance().showMainMenu
      SwingUtilities.invokeLater { updateCustomTitleBar() }
    }

    menuHolder.isVisible = UISettings.getInstance().showMainMenu
  }

  override suspend fun updateMenuActions(forceRebuild: Boolean) {
    ideMenu.updateMenuActions(forceRebuild = forceRebuild)
  }

  override fun getComponent(): JComponent = this

  override fun updateActive() {
    super.updateActive()
    headerTitle.setActive(myActive)
  }

  override fun installListeners() {
    ideMenu.selectionModel.addChangeListener(changeListener)
    val disp = Disposer.newCheckedDisposable()
    Disposer.register(ApplicationManager.getApplication(), disp)

    ApplicationManager.getApplication().messageBus.connect(disp).subscribe(UISettingsListener.TOPIC, mainMenuUpdater)
    mainMenuUpdater.uiSettingsChanged(UISettings.getInstance())
    disposable = disp

    super.installListeners()
  }

  override fun uninstallListeners() {
    ideMenu.selectionModel.removeChangeListener(changeListener)
    disposable?.let {
      if (!it.isDisposed) {
        Disposer.dispose(it)
      }
      disposable = null
    }

    super.uninstallListeners()
  }
}