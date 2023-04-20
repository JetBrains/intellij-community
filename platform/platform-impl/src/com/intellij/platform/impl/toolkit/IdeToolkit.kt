@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")
// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.impl.toolkit

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.Disposer
import sun.awt.LightweightFrame
import sun.awt.SunToolkit
import java.awt.*
import java.awt.List
import java.awt.datatransfer.Clipboard
import java.awt.font.TextAttribute
import java.awt.im.InputMethodHighlight
import java.awt.im.spi.InputMethodDescriptor
import java.awt.image.ImageObserver
import java.awt.peer.*
import java.util.*

class IdeToolkit : SunToolkit() {
  companion object {
    @JvmStatic
    private val logger = logger<IdeToolkit>()
    val instance: IdeToolkit
      get() = Toolkit.getDefaultToolkit() as IdeToolkit

    private val isPluginEnabled: Boolean by lazy {
      PluginManagerCore.getPluginSet().isPluginEnabled(PluginId.getId("com.jetbrains.codeWithMe"))
    }

    private val clientInstance: ClientToolkit
      get() {
        assert(isPluginEnabled) { "CodeWithMe plugin is not enabled" }
        return service()
      }

    private val clipboard = Clipboard("System")
  }
  fun peerCreated(target: Component, peer: ComponentPeer, disposable: Disposable) {
    targetCreatedPeer(target, peer)
    Disposer.register(disposable) { targetDisposedPeer(target, peer) }
  }
  fun createPanelWindow(panel: Component, target: Window) = clientInstance.createPanelWindow(panel, target)
  override fun createWindow(target: Window) = clientInstance.createWindow(target)
  override fun createDialog(target: Dialog) = clientInstance.createDialog(target)
  override fun createFrame(target: Frame) = clientInstance.createFrame(target)
  override fun getSystemClipboard() = clipboard

  override fun getScreenResolution() = 96
  override fun prepareImage(img: Image, w: Int, h: Int, o: ImageObserver?): Boolean {
    //TODO: probably can be used for caching
    return super.prepareImage(img, w, h, o)
  }

  override fun createDesktopPeer(target: Desktop) = IdeDesktopPeer()
  override fun getMouseInfoPeer() = IdeMouseInfoPeer
  override fun getKeyboardFocusManagerPeer() = IdeKeyboardFocusManagerPeer

  // Toolkit interface which shouldn't be used
  override fun sync() {}
  override fun getPrintJob(frame: Frame?, jobtitle: String?, props: Properties?): PrintJob? = null
  override fun beep() {}
  override fun createFileDialog(target: FileDialog): FileDialogPeer {
    error("Not implemented")
  }
  override fun createButton(target: Button): ButtonPeer {
    error("Not implemented")
  }

  override fun createTextField(target: TextField): TextFieldPeer {
    error("Not implemented")
  }

  override fun createLabel(target: Label): LabelPeer {
    error("Not implemented")
  }

  override fun createList(target: List): ListPeer {
    error("Not implemented")
  }

  override fun createCheckbox(target: Checkbox): CheckboxPeer {
    error("Not implemented")
  }

  override fun createScrollbar(target: Scrollbar): ScrollbarPeer {
    error("Not implemented")
  }

  override fun createScrollPane(target: ScrollPane): ScrollPanePeer {
    error("Not implemented")
  }

  override fun createTextArea(target: TextArea): TextAreaPeer {
    error("Not implemented")
  }

  override fun createChoice(target: Choice): ChoicePeer {
    error("Not implemented")
  }

  override fun createCanvas(target: Canvas): CanvasPeer {
    error("Not implemented")
  }

  override fun createPanel(target: Panel): PanelPeer {
    error("Not implemented")
  }

  override fun createMenuBar(target: MenuBar): MenuBarPeer {
    error("Not implemented")
  }

  override fun createMenu(target: Menu): MenuPeer {
    error("Not implemented")
  }

  override fun createPopupMenu(target: PopupMenu): PopupMenuPeer {
    error("Not implemented")
  }

  override fun createMenuItem(target: MenuItem): MenuItemPeer {
    error("Not implemented")
  }

  override fun createCheckboxMenuItem(target: CheckboxMenuItem): CheckboxMenuItemPeer {
    error("Not implemented")
  }

  override fun getInputMethodAdapterDescriptor(): InputMethodDescriptor? = null

  override fun mapInputMethodHighlight(highlight: InputMethodHighlight): Map<TextAttribute, *> {
    error("Not implemented")
  }


  override fun createLightweightFrame(target: LightweightFrame): FramePeer {
    error("Not implemented")
  }

  override fun createTrayIcon(target: TrayIcon?): TrayIconPeer? = null

  override fun createSystemTray(target: SystemTray?): SystemTrayPeer? = null

  override fun isTraySupported() = false

  override fun syncNativeQueue(timeout: Long) = true

  override fun grab(w: Window?) {
    //todo
  }

  override fun ungrab(w: Window?) {
    //todo
  }

  override fun isDesktopSupported() = true
  override fun isTaskbarSupported() = false
}