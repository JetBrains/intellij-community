// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")
package com.intellij.platform.impl.toolkit

import com.intellij.openapi.Disposable
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
    fun getInstance(): IdeToolkit = getDefaultToolkit() as IdeToolkit

    private val clipboard = Clipboard("System")
  }

  fun clientInstance() = ClientToolkit.getInstance()

  fun peerCreated(target: Component, peer: ComponentPeer, disposable: Disposable) {
    targetCreatedPeer(target, peer)
    Disposer.register(disposable) { targetDisposedPeer(target, peer) }
  }

  override fun createWindow(target: Window): WindowPeer = clientInstance().createWindow(target)
  override fun createDialog(target: Dialog): DialogPeer = clientInstance().createDialog(target)
  override fun createFrame(target: Frame): FramePeer = clientInstance().createFrame(target)
  override fun getSystemClipboard(): Clipboard = clipboard

  override fun getScreenResolution(): Int = 96
  override fun prepareImage(img: Image, w: Int, h: Int, o: ImageObserver?): Boolean {
    //TODO: probably can be used for caching
    return super.prepareImage(img, w, h, o)
  }

  override fun createDesktopPeer(target: Desktop): DesktopPeer = IdeDesktopPeer()
  override fun getMouseInfoPeer(): IdeMouseInfoPeer = IdeMouseInfoPeer
  override fun getKeyboardFocusManagerPeer(): IdeKeyboardFocusManagerPeer = IdeKeyboardFocusManagerPeer

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

  override fun isTraySupported(): Boolean = false

  override fun syncNativeQueue(timeout: Long): Boolean = true

  override fun grab(w: Window?) {
    //todo
  }

  override fun ungrab(w: Window?) {
    //todo
  }

  override fun isDesktopSupported(): Boolean = true
  override fun isTaskbarSupported(): Boolean = false

  //TODO(sviatoslav.vlasov): Pass parameters from original toolkit into constructor
  override fun areExtraMouseButtonsEnabled() = true
  override fun getNumberOfButtons() = 5

  override fun initializeDesktopProperties() {
    desktopProperties["jb.swing.avoid.text.layout"] = true // enables special mode of IME composed text painting in JBR (JBR-5946)
  }
}