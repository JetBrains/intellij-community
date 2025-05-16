// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")
package com.intellij.platform.impl.toolkit

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.util.IncorrectOperationException
import org.jetbrains.annotations.ApiStatus.Internal
import sun.awt.AWTAccessor
import sun.awt.LightweightFrame
import sun.awt.SunToolkit
import java.awt.*
import java.awt.List
import java.awt.datatransfer.Clipboard
import java.awt.event.InputEvent
import java.awt.font.TextAttribute
import java.awt.im.InputMethodHighlight
import java.awt.im.spi.InputMethodDescriptor
import java.awt.image.ImageObserver
import java.awt.peer.*
import java.util.*

@Internal
class IdeToolkit : SunToolkit() {
  companion object {
    @JvmStatic
    fun getInstance(): IdeToolkit = getDefaultToolkit() as IdeToolkit

    private val clipboard = IdeClipboard()
  }

  private var macClient = false

  fun setMacClient(value: Boolean) {
    macClient = value
    AWTAccessor.getToolkitAccessor().setPlatformResources(if (value) MacKeysResourceBundle else null)
  }

  fun clientInstance() = ClientToolkit.getInstance()

  fun peerCreated(target: Component, peer: ComponentPeer, disposable: Disposable) {
    targetCreatedPeer(target, peer)
    if (!Disposer.tryRegister(disposable) { targetDisposedPeer(target, peer) }) {
      throw IncorrectOperationException("Provided disposable " +
                                        "($disposable, class=${disposable.javaClass}, hash=${System.identityHashCode(disposable)}) " +
                                        "has already been disposed (see the cause for stacktrace), cannot register $target for disposal",
                                        Disposer.getDisposalTrace(disposable))
    }
  }

  override fun createWindow(target: Window): WindowPeer = clientInstance().createWindow(target)
  override fun createDialog(target: Dialog): DialogPeer = clientInstance().createDialog(target)
  override fun createFrame(target: Frame): FramePeer = clientInstance().createFrame(target)
  override fun getSystemClipboard(): Clipboard = clipboard

  override fun createRobot(screen: GraphicsDevice?): RobotPeer? = clientInstance().createRobot(screen)
  override fun getMouseInfoPeer(): IdeMouseInfoPeer = IdeMouseInfoPeer

  override fun getScreenResolution(): Int = 96
  override fun prepareImage(img: Image, w: Int, h: Int, o: ImageObserver?): Boolean {
    //TODO: probably can be used for caching
    return super.prepareImage(img, w, h, o)
  }

  override fun createDesktopPeer(target: Desktop): DesktopPeer = IdeDesktopPeer()
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

  @Suppress("DEPRECATION")
  @Deprecated("Deprecated in Java", ReplaceWith("getMenuShortcutKeyMaskEx()"))
  override fun getMenuShortcutKeyMask() = if (macClient) Event.META_MASK else Event.CTRL_MASK
  override fun getMenuShortcutKeyMaskEx() = if (macClient) InputEvent.META_DOWN_MASK else InputEvent.CTRL_DOWN_MASK
}

private object MacKeysResourceBundle : ListResourceBundle() {
  override fun getContents() = arrayOf(
    arrayOf("AWT.shift", "\u21e7"),
    arrayOf("AWT.control", "\u2303"),
    arrayOf("AWT.alt", "\u2325"),
    arrayOf("AWT.meta", "\u2318"),
    arrayOf("AWT.altGraph", "\u2325"),
    arrayOf("AWT.enter", "\u23ce"),
    arrayOf("AWT.backSpace", "\u232b"),
    arrayOf("AWT.tab", "\u21e5"),
    arrayOf("AWT.cancel", "\u238b"),
    arrayOf("AWT.clear", "\u2327"),
    arrayOf("AWT.capsLock", "\u21ea"),
    arrayOf("AWT.escape", "\u238b"),
    arrayOf("AWT.space", "\u2423"),
    arrayOf("AWT.pgup", "\u21de"),
    arrayOf("AWT.pgdn", "\u21df"),
    arrayOf("AWT.end", "\u2198"),
    arrayOf("AWT.home", "\u2196"),
    arrayOf("AWT.left", "\u2190"),
    arrayOf("AWT.up", "\u2191"),
    arrayOf("AWT.right", "\u2192"),
    arrayOf("AWT.down", "\u2193"),
    arrayOf("AWT.comma", ","),
    arrayOf("AWT.period", "."),
    arrayOf("AWT.slash", "/"),
    arrayOf("AWT.semicolon", ";"),
    arrayOf("AWT.equals", "="),
    arrayOf("AWT.openBracket", "["),
    arrayOf("AWT.backSlash", "\\"),
    arrayOf("AWT.closeBracket", "]"),
    arrayOf("AWT.multiply", "\u2328 *"),
    arrayOf("AWT.add", "\u2328 +"),
    arrayOf("AWT.separator", "\u2328 ,"),
    arrayOf("AWT.subtract", "\u2328 -"),
    arrayOf("AWT.decimal", "\u2328 ."),
    arrayOf("AWT.divide", "\u2328 /"),
    arrayOf("AWT.delete", "\u2326"),
    arrayOf("AWT.printScreen", "\u2399"),
    arrayOf("AWT.backQuote", "`"),
    arrayOf("AWT.quote", "'"),
    arrayOf("AWT.ampersand", "&"),
    arrayOf("AWT.asterisk", "*"),
    arrayOf("AWT.quoteDbl", "\""),
    arrayOf("AWT.Less", "<"),
    arrayOf("AWT.greater", ">"),
    arrayOf("AWT.braceLeft", "["),
    arrayOf("AWT.braceRight", "]"),
    arrayOf("AWT.at", "@"),
    arrayOf("AWT.colon", ":"),
    arrayOf("AWT.circumflex", "^"),
    arrayOf("AWT.dollar", "$"),
    arrayOf("AWT.euro", "\u20ac"),
    arrayOf("AWT.exclamationMark", "!"),
    arrayOf("AWT.invertedExclamationMark", "\u00a1"),
    arrayOf("AWT.leftParenthesis", "("),
    arrayOf("AWT.numberSign", "#"),
    arrayOf("AWT.plus", "+"),
    arrayOf("AWT.minus", "-"),
    arrayOf("AWT.rightParenthesis", ")"),
    arrayOf("AWT.underscore", "_"),
    arrayOf("AWT.numpad", "\u2328")
  )
}