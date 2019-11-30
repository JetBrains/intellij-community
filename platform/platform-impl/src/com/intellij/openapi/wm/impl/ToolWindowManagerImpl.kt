// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeEventQueue
import com.intellij.ide.actions.ActivateToolWindowAction
import com.intellij.ide.actions.MaximizeActiveDialogAction
import com.intellij.internal.statistic.collectors.fus.actions.persistence.ToolWindowCollector
import com.intellij.notification.impl.NotificationsManagerImpl
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.EditorWithProviderComposite
import com.intellij.openapi.fileEditor.impl.EditorsSplitters
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.keymap.KeymapManagerListener
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.*
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.Splitter
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.*
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.*
import com.intellij.openapi.wm.ex.*
import com.intellij.openapi.wm.impl.ToolWindowManagerImpl
import com.intellij.openapi.wm.impl.commands.ApplyWindowInfoCmd
import com.intellij.openapi.wm.impl.commands.FinalizableCommand
import com.intellij.openapi.wm.impl.commands.InvokeLaterCmd
import com.intellij.openapi.wm.impl.commands.RequestFocusInToolWindowCmd
import com.intellij.ui.BalloonImpl
import com.intellij.ui.ColorUtil
import com.intellij.ui.ComponentUtil
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.*
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.*
import com.intellij.util.ui.update.UiNotifyConnector
import org.intellij.lang.annotations.JdkConstants
import org.jdom.Element
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.TestOnly
import java.awt.*
import java.awt.event.*
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.function.BooleanSupplier
import java.util.function.Predicate
import javax.swing.*
import javax.swing.event.HyperlinkEvent
import javax.swing.event.HyperlinkListener

@State(name = "ToolWindowManager",
       defaultStateAsResource = true,
       storages = [Storage(StoragePathMacros.PRODUCT_WORKSPACE_FILE), Storage(value = StoragePathMacros.WORKSPACE_FILE, deprecated = true)])
open class ToolWindowManagerImpl(val project: Project) : ToolWindowManagerEx(), PersistentStateComponent<Element?> {
  private val myDispatcher = EventDispatcher.create(ToolWindowManagerListener::class.java)
  private var myLayout = DesktopLayout()
  private val idToEntry: MutableMap<String, ToolWindowEntry> = HashMap()
  private val myToolWindowPropertyChangeListener = MyToolWindowPropertyChangeListener()
  private val myInternalDecoratorListener: InternalDecoratorListener = MyInternalDecoratorListener()
  private val myActiveStack = ActiveStack()
  private val mySideStack = SideStack()
  private var myToolWindowPane: ToolWindowsPane? = null
  private var myFrame: ProjectFrameHelper? = null
  private var myLayoutToRestoreLater: DesktopLayout? = null
  private var myCurrentState = KeyState.waiting
  private val myWaiterForSecondPress = Alarm()
  private val mySecondPressRunnable = Runnable {
    if (myCurrentState != KeyState.hold) {
      resetHoldState()
    }
  }

  fun isToolWindowRegistered(id: String): Boolean {
    return myLayout.isToolWindowRegistered(id)
  }

  private enum class KeyState {
    waiting, pressed, released, hold
  }

  private val myCommandProcessor = CommandProcessor()
  private fun updateToolWindowHeaders() {
    focusManager.doWhenFocusSettlesDown(object : ExpirableRunnable.ForProject(project) {
      override fun run() {
        for (each: WindowInfoImpl in myLayout.infos) {
          if (each.isVisible) {
            val tw = getToolWindow(each.id)
            if (tw is ToolWindowImpl) {
              val decorator = tw.decorator
              decorator?.repaint()
            }
          }
        }
      }
    })
  }

  fun dispatchKeyEvent(e: KeyEvent): Boolean {
    if ((e.keyCode != KeyEvent.VK_CONTROL) && (
        e.keyCode != KeyEvent.VK_ALT) && (
          e.keyCode != KeyEvent.VK_SHIFT) && (
          e.keyCode != KeyEvent.VK_META)) {
      if (e.modifiers == 0) {
        resetHoldState()
      }
      return false
    }
    if (e.id != KeyEvent.KEY_PRESSED && e.id != KeyEvent.KEY_RELEASED) return false
    val parent = UIUtil.findUltimateParent(e.component)
    if (parent is IdeFrame) {
      if ((parent as IdeFrame).project !== project) {
        resetHoldState()
        return false
      }
    }
    val vks = activateToolWindowVKsMask
    if (vks == 0) {
      resetHoldState()
      return false
    }
    val mouseMask = InputEvent.BUTTON1_DOWN_MASK or InputEvent.BUTTON2_DOWN_MASK or InputEvent.BUTTON3_DOWN_MASK
    if (BitUtil.isSet(vks, keyCodeToInputMask(
        e.keyCode)) && (e.modifiersEx and mouseMask) == 0) {
      val pressed = e.id == KeyEvent.KEY_PRESSED
      val modifiers = e.modifiers
      if (areAllModifiersPressed(modifiers, vks) || !pressed) {
        processState(pressed)
      }
      else {
        resetHoldState()
      }
    }
    return false
  }

  private fun resetHoldState() {
    myCurrentState = KeyState.waiting
    processHoldState()
  }

  private fun processState(pressed: Boolean) {
    if (pressed) {
      if (myCurrentState == KeyState.waiting) {
        myCurrentState = KeyState.pressed
      }
      else if (myCurrentState == KeyState.released) {
        myCurrentState = KeyState.hold
        processHoldState()
      }
    }
    else {
      if (myCurrentState == KeyState.pressed) {
        myCurrentState = KeyState.released
        restartWaitingForSecondPressAlarm()
      }
      else {
        resetHoldState()
      }
    }
  }

  private fun processHoldState() {
    if (myToolWindowPane != null) {
      myToolWindowPane!!.setStripesOverlayed(myCurrentState == KeyState.hold)
    }
  }

  private fun restartWaitingForSecondPressAlarm() {
    myWaiterForSecondPress.cancelAllRequests()
    myWaiterForSecondPress.addRequest(mySecondPressRunnable,
                                      SystemProperties.getIntProperty("actionSystem.keyGestureDblClickTime", 650))
  }

  private fun hasOpenEditorFiles(): Boolean {
    return FileEditorManager.getInstance(project).openFiles.size > 0
  }

  @TestOnly
  fun init() {
    if (myToolWindowPane != null) {
      return
    }
    myFrame = WindowManagerEx.getInstanceEx().allocateFrame(project)
    val frame = myFrame!!.frame
    myToolWindowPane = ToolWindowsPane(frame, this, project)
    val rootPane: IdeRootPane = Objects.requireNonNull(myFrame!!.rootPane)
    rootPane.setToolWindowsPane(myToolWindowPane)
    frame.title = FrameTitleBuilder.getInstance().getProjectTitle(project)
    rootPane.updateToolbar()
    IdeEventQueue.getInstance().addDispatcher(IdeEventQueue.EventDispatcher { e: AWTEvent ->
      if (e is KeyEvent) {
        dispatchKeyEvent(e)
      }
      if (e is WindowEvent && (e.getID() == WindowEvent.WINDOW_LOST_FOCUS) && (e.getSource() === myFrame)) {
        resetHoldState()
      }
      false
    }, project)
    UIUtil.putClientProperty(myToolWindowPane!!,
                             UIUtil.NOT_IN_HIERARCHY_COMPONENTS,
                             Iterable {
                               val infos: List<WindowInfoImpl> = myLayout.getInfos()
                               val result: MutableList<JComponent> = ArrayList(
                                 infos.size)
                               for (info: WindowInfoImpl in infos) {
                                 val decorator: JComponent = getInternalDecorator(
                                   (info.id)!!)
                                 if (decorator.getParent() == null) {
                                   result.add(decorator)
                                 }
                               }
                               result.iterator()
                             })
    checkInvariants("")
  }

  private fun initAll(commandsList: MutableList<in FinalizableCommand>) {
    appendUpdateRootPaneCmd(commandsList)
    val editorComponent = createEditorComponent(project)
    editorComponent.isFocusable = false
    appendSetEditorComponentCmd(editorComponent, commandsList)
  }

  private fun registerToolWindowsFromBeans(list: MutableList<in FinalizableCommand>) {
    for (bean: ToolWindowEP in ToolWindowEP.EP_NAME.extensionList) {
      list.add(object : FinalizableCommand(EmptyRunnable.INSTANCE) {
        override fun run() {
          try {
            initToolWindow(bean)
          }
          catch (e: ProcessCanceledException) {
            throw e
          }
          catch (t: Throwable) {
            LOG.error("failed to init toolwindow " + bean.factoryClass, t)
          }
        }
      })
    }
  }

  override fun initToolWindow(bean: ToolWindowEP) {
    val condition = bean.condition
    if (condition != null && !condition.value(project)) {
      return
    }
    val before = myLayout.getInfo(bean.id, false)
    val visible = before != null && before.isVisible
    val label = createInitializingLabel()
    val toolWindowAnchor = ToolWindowAnchor.fromText(bean.anchor)
    val factory = bean.toolWindowFactory
    val window = registerToolWindow(bean.id, label, toolWindowAnchor, false, bean.canCloseContents,
                                    DumbService.isDumbAware(factory),
                                    factory.shouldBeAvailable(project))
    val toolWindow = window as ToolWindowImpl
    toolWindow.setContentFactory(factory)
    if (bean.icon != null && toolWindow.icon == null) {
      var icon = IconLoader.findIcon(bean.icon, factory.javaClass)
      if (icon == null) {
        try {
          icon = IconLoader.getIcon(bean.icon)
        }
        catch (ignored: Exception) {
          icon = EmptyIcon.ICON_13
        }
      }
      toolWindow.setIcon((icon)!!)
    }
    val info = getRegisteredInfoOrLogError(bean.id)
    if (!info.isSplit && bean.secondary && !info.isWasRead) {
      toolWindow.setSplitMode(true, null)
    }
    val runnable = label@ DumbAwareRunnable {
      if (toolWindow.isDisposed()) return@label
      toolWindow.ensureContentInitialized()
    }
    if (visible) {
      runnable.run()
    }
    else {
      UiNotifyConnector.doWhenFirstShown(label, { ApplicationManager.getApplication().invokeLater(runnable) })
    }
  }

  fun projectClosed() {
    if (myFrame == null) {
      return
    }
    myFrame!!.releaseFrame()
    val commandsList: MutableList<FinalizableCommand> = ArrayList()
    appendUpdateRootPaneCmd(commandsList)
    // hide all tool windows
    for (info: WindowInfoImpl in myLayout.infos) {
      deactivateToolWindowImpl(info, true, commandsList)
    }
    appendSetEditorComponentCmd(null, commandsList)
    // do not notify - project is disposed
    execute(commandsList,  /* isFireStateChangedEvent */false)
    myFrame = null
  }

  override fun addToolWindowManagerListener(listener: ToolWindowManagerListener) {
    myDispatcher.addListener(listener)
  }

  override fun addToolWindowManagerListener(listener: ToolWindowManagerListener,
                                            parentDisposable: Disposable) {
    project.messageBus.connect(parentDisposable).subscribe(ToolWindowManagerListener.TOPIC, listener)
  }

  override fun removeToolWindowManagerListener(listener: ToolWindowManagerListener) {
    myDispatcher.removeListener(listener)
  }

  fun execute(commandList: List<FinalizableCommand>) {
    execute(commandList, true)
  }

  /**
   * This is helper method. It delegated its functionality to the WindowManager.
   * Before delegating it fires state changed.
   */
  private fun execute(commandList: List<FinalizableCommand>, isFireStateChangedEvent: Boolean) {
    if (commandList.isEmpty()) {
      return
    }
    if (isFireStateChangedEvent) {
      for (each: FinalizableCommand in commandList) {
        if (each.willChangeState()) {
          fireStateChanged()
          break
        }
      }
    }
    for (each: FinalizableCommand in commandList) {
      each.beforeExecute(this)
    }
    myCommandProcessor.execute(commandList, { project.isDisposed() })
  }

  private fun flushCommands() {
    myCommandProcessor.flush()
  }

  override fun activateEditorComponent() {
    focusDefaultElementInSelectedEditor()
  }

  private fun deactivateWindows(idToIgnore: String, commandList: MutableList<in FinalizableCommand>) {
    for (info: WindowInfoImpl in myLayout.infos) {
      if (idToIgnore != info.id) {
        deactivateToolWindowImpl(info, isToHideOnDeactivation(info), commandList)
      }
    }
  }

  /**
   * Helper method. It makes window visible, activates it and request focus into the tool window.
   * But it doesn't deactivate other tool windows. Use `prepareForActivation` method to
   * deactivates other tool windows.
   *
   * @param dirtyMode if `true` then all UI operations are performed in "dirty" mode.
   * It means that UI isn't validated and repainted just after each add/remove operation.
   */
  private fun showAndActivate(id: String,
                              dirtyMode: Boolean,
                              commandsList: MutableList<in FinalizableCommand>,
                              autoFocusContents: Boolean) {
    if (!getToolWindow(id)!!.isAvailable) {
      return
    }
    // show activated
    val info = getRegisteredInfoOrLogError(id)
    var toApplyInfo = false
    if (!info.isActive) {
      info.isActive = true
      toApplyInfo = true
    }
    showToolWindowImpl(id, dirtyMode, commandsList)
    // activate
    if (toApplyInfo) {
      appendApplyWindowInfoCmd(info, commandsList)
      myActiveStack.push(id)
    }
    if (autoFocusContents && ApplicationManager.getApplication().isActive) {
      appendRequestFocusInToolWindowCmd(id, commandsList)
    }
  }

  fun activateToolWindow(id: String, forced: Boolean, autoFocusContents: Boolean) {
    if (LOG.isDebugEnabled) {
      LOG.debug("enter: activateToolWindow($id)")
    }
    ApplicationManager.getApplication().assertIsDispatchThread()
    checkId(id)
    val commandList: MutableList<FinalizableCommand> = ArrayList()
    activateToolWindowImpl(id, commandList, forced, autoFocusContents)
    execute(commandList)
  }

  private fun activateToolWindowImpl(id: String,
                                     commandList: MutableList<in FinalizableCommand>,
                                     forced: Boolean,
                                     autoFocusContents: Boolean) {
    var autoFocusContents = autoFocusContents
    ToolWindowCollector.recordActivation(id)
    autoFocusContents = autoFocusContents and forced
    if (LOG.isDebugEnabled) {
      LOG.debug("enter: activateToolWindowImpl($id)")
    }
    if (!getToolWindow(id)!!.isAvailable) { // Tool window can be "logically" active but not focused. For example,
      // when the user switched to another application. So we just need to bring
      // tool window's window to front.
      val decorator = getInternalDecorator(id)
      if (!decorator.hasFocus() && autoFocusContents) {
        appendRequestFocusInToolWindowCmd(id, commandList)
      }
      return
    }
    deactivateWindows(id, commandList)
    showAndActivate(id, false, commandList, autoFocusContents)
  }

  /**
   * Checks whether the specified `id` defines installed tool
   * window. If it's not then throws `IllegalStateException`.
   *
   * @throws IllegalStateException if tool window isn't installed.
   */
  private fun checkId(id: String) {
    getRegisteredInfoOrLogError(id)
  }

  private fun getRegisteredInfoOrLogError(id: String): WindowInfoImpl {
    val info = myLayout.getInfo(id, false) ?: throw IllegalThreadStateException("window with id=\"$id\" is unknown")
    if (!info.isRegistered) {
      LOG.error("window with id=\"$id\" isn't registered")
    }
    return info
  }

  /**
   * Helper method. It deactivates (and hides) window with specified `id`.
   */
  private fun deactivateToolWindowImpl(info: WindowInfoImpl,
                                       shouldHide: Boolean,
                                       commandsList: MutableList<in FinalizableCommand>) {
    if (LOG.isDebugEnabled) {
      LOG.debug("enter: deactivateToolWindowImpl(" + info.id + "," + shouldHide + ")")
    }
    if (shouldHide) {
      appendRemoveDecoratorCommand(info, false, commandsList)
    }
    info.isActive = false
    appendApplyWindowInfoCmd(info, commandsList)
    checkInvariants("Info: $info; shouldHide: $shouldHide")
  }

  override fun getToolWindowIds(): Array<String> {
    val infos = myLayout.infos
    val ids = ArrayUtil.newStringArray(infos.size)
    for (i in infos.indices) {
      ids[i] = (infos[i].id)!!
    }
    return ids
  }

  override fun getActiveToolWindowId(): String? {
    ApplicationManager.getApplication().assertIsDispatchThread()
    return myLayout.activeId
  }

  override fun getLastActiveToolWindowId(): String? {
    return getLastActiveToolWindowId(null)
  }

  override fun getLastActiveToolWindowId(condition: Condition<in JComponent>?): String? {
    ApplicationManager.getApplication().assertIsDispatchThread()
    var lastActiveToolWindowId: String? = null
    for (i in 0 until myActiveStack.persistentSize) {
      val id = myActiveStack.peekPersistent(i)
      val toolWindow = getToolWindow(id)
      LOG.assertTrue(toolWindow != null)
      if (toolWindow!!.isAvailable) {
        if (condition == null || condition.value(toolWindow.component)) {
          lastActiveToolWindowId = id
          break
        }
      }
    }
    return lastActiveToolWindowId
  }

  /**
   * @return floating decorator for the tool window with specified `ID`.
   */
  private fun getFloatingDecorator(id: String): FloatingDecorator? {
    val entry = idToEntry[id]
    return entry?.floatingDecorator
  }

  /**
   * @return windowed decorator for the tool window with specified `ID`.
   */
  private fun getWindowedDecorator(id: String): WindowedDecorator? {
    val entry = idToEntry[id]
    return entry?.windowedDecorator
  }

  /**
   * @return internal decorator for the tool window with specified `ID`.
   */
  fun getInternalDecorator(id: String): InternalDecorator {
    return idToEntry[id]!!.internalDecorator
  }

  /**
   * @return tool button for the window with specified `ID`.
   */
  fun getStripeButton(id: String): StripeButton {
    return idToEntry[id]!!.stripeButton
  }

  override fun getIdsOn(anchor: ToolWindowAnchor): List<String> {
    return myLayout.getVisibleIdsOn(anchor, this)
  }

  override fun getLocationIcon(id: String, fallbackIcon: Icon): Icon {
    val window = getToolWindow(id)
    val info = myLayout.getInfo(id, false)
    if (window != null || info != null) {
      val type = if (window != null) window.type else info!!.type
      if (type == ToolWindowType.FLOATING || type == ToolWindowType.WINDOWED) {
        return AllIcons.Actions.MoveToWindow
      }
      val anchor = if (window != null) window.anchor else info!!.anchor
      val splitMode = window?.isSplitMode ?: info!!.isSplit
      if ((ToolWindowAnchor.BOTTOM == anchor)) {
        return if (splitMode) AllIcons.Actions.MoveToBottomRight else AllIcons.Actions.MoveToBottomLeft
      }
      else if ((ToolWindowAnchor.LEFT == anchor)) {
        return if (splitMode) AllIcons.Actions.MoveToLeftBottom else AllIcons.Actions.MoveToLeftTop
      }
      else if ((ToolWindowAnchor.RIGHT == anchor)) {
        return if (splitMode) AllIcons.Actions.MoveToRightBottom else AllIcons.Actions.MoveToRightTop
      }
      else if ((ToolWindowAnchor.TOP == anchor)) {
        return if (splitMode) AllIcons.Actions.MoveToTopRight else AllIcons.Actions.MoveToTopLeft
      }
    }
    return fallbackIcon
  }

  // cannot be ToolWindowEx because of backward compatibility
  override fun getToolWindow(id: String?): ToolWindow? {
    return if (id == null || !myLayout.isToolWindowRegistered(id)) {
      null
    }
    else getInternalDecorator(id).toolWindow
  }

  fun showToolWindow(id: String) {
    if (LOG.isDebugEnabled) {
      LOG.debug("enter: showToolWindow($id)")
    }
    ApplicationManager.getApplication().assertIsDispatchThread()
    val commandList: MutableList<FinalizableCommand> = ArrayList()
    showToolWindowImpl(id, false, commandList)
    execute(commandList)
  }

  override fun hideToolWindow(id: String, hideSide: Boolean) {
    hideToolWindow(id, hideSide, true)
  }

  fun hideToolWindow(id: String, hideSide: Boolean, moveFocus: Boolean) {
    ApplicationManager.getApplication().assertIsDispatchThread()
    val info = getRegisteredInfoOrLogError(id)
    val commandList: MutableList<FinalizableCommand> = ArrayList()
    val wasActive = info.isActive
    // hide and deactivate
    deactivateToolWindowImpl(info, true, commandList)
    if (hideSide && !info.isFloating && !info.isWindowed) {
      val ids = myLayout.getVisibleIdsOn(info.anchor, this)
      for (each: String? in ids) {
        myActiveStack.remove((each)!!, true)
      }
      if (isStackEnabled) {
        while (!mySideStack.isEmpty(info.anchor)) {
          mySideStack.pop(info.anchor)
        }
      }
      for (eachInfo: WindowInfoImpl in myLayout.infos) {
        if (eachInfo.isVisible && eachInfo.anchor == info.anchor) {
          deactivateToolWindowImpl(eachInfo, true, commandList)
        }
      }
    }
    else { // first of all we have to find tool window that was located at the same side and was hidden
      if (isStackEnabled) {
        var info2: WindowInfoImpl? = null
        while (!mySideStack.isEmpty(info.anchor)) {
          val storedInfo = mySideStack.pop(info.anchor)
          if (storedInfo.isSplit != info.isSplit) {
            continue
          }
          val currentInfo = getRegisteredInfoOrLogError((Objects.requireNonNull(storedInfo.id))!!)
          // SideStack contains copies of real WindowInfos. It means that
          // these stored infos can be invalid. The following loop removes invalid WindowInfos.
          if ((storedInfo.anchor == currentInfo.anchor) && (
              storedInfo.type == currentInfo.type) && (
                storedInfo.isAutoHide == currentInfo.isAutoHide)) {
            info2 = storedInfo
            break
          }
        }
        if (info2 != null) {
          showToolWindowImpl((Objects.requireNonNull(info2.id))!!, false, commandList)
        }
      }
      // If we hide currently active tool window then we should activate the previous
      // one which is located in the tool window stack.
      // Activate another tool window if no active tool window exists and
      // window stack is enabled.
      myActiveStack.remove(id, false) // hidden window should be at the top of stack
      if (wasActive && moveFocus && !myActiveStack.isEmpty) {
        val toBeActivatedId = myActiveStack.pop()
        if (getRegisteredInfoOrLogError(toBeActivatedId).isVisible || isStackEnabled) {
          activateToolWindowImpl(toBeActivatedId, commandList, false, true)
        }
        else {
          focusToolWindowByDefault(id)
        }
      }
    }
    execute(commandList)
  }

  /**
   * @param dirtyMode if `true` then all UI operations are performed in dirty mode.
   */
  private fun showToolWindowImpl(id: String,
                                 dirtyMode: Boolean,
                                 commandsList: MutableList<in FinalizableCommand>) {
    val toBeShownInfo = getRegisteredInfoOrLogError(id)
    val window = getToolWindow(id)
    if (window != null && toBeShownInfo.isWindowed) {
      UIUtil.toFront(UIUtil.getWindow(window.component))
    }
    if (toBeShownInfo.isVisible || (window == null) || !window.isAvailable) {
      return
    }
    toBeShownInfo.isVisible = true
    val entry = idToEntry[id]
    if (toBeShownInfo.isFloating) {
      commandsList.add(AddFloatingDecoratorCmd((entry)!!, toBeShownInfo))
    }
    else if (toBeShownInfo.isWindowed) {
      commandsList.add(AddWindowedDecoratorCmd((entry)!!, toBeShownInfo))
    }
    else { // docked and sliding windows
      // If there is tool window on the same side then we have to hide it, i.e.
      // clear place for tool window to be shown.
      //
      // We store WindowInfo of hidden tool window in the SideStack (if the tool window
      // is docked and not auto-hide one). Therefore it's possible to restore the
      // hidden tool window when showing tool window will be closed.
      for (info: WindowInfoImpl in myLayout.infos) {
        if ((id == info.id)) {
          continue
        }
        if (info.isVisible && (
            info.type == toBeShownInfo.type) && (
              info.anchor == toBeShownInfo.anchor) && (
              info.isSplit == toBeShownInfo.isSplit)) { // hide and deactivate tool window
          info.isVisible = false
          appendRemoveDecoratorCmd((Objects.requireNonNull(info.id))!!, false, commandsList)
          if (info.isActive) {
            info.isActive = false
          }
          appendApplyWindowInfoCmd(info, commandsList)
          // store WindowInfo into the SideStack
          if (isStackEnabled) {
            if (info.isDocked && !info.isAutoHide) {
              mySideStack.push(info)
            }
          }
        }
      }
      appendAddDecoratorCmd(entry!!.internalDecorator, toBeShownInfo, dirtyMode, commandsList)
      // Remove tool window from the SideStack.
      if (isStackEnabled) {
        mySideStack.remove(id)
      }
    }
    if (!toBeShownInfo.isShowStripeButton) {
      toBeShownInfo.isShowStripeButton = true
    }
    appendApplyWindowInfoCmd(toBeShownInfo, commandsList)
    checkInvariants("Id: $id; dirtyMode: $dirtyMode")
  }

  override fun registerToolWindow(id: String,
                                  component: JComponent,
                                  anchor: ToolWindowAnchor): ToolWindow {
    return registerToolWindow(id, component, anchor, false, false, false, true)
  }

  override fun registerToolWindow(id: String,
                                  component: JComponent,
                                  anchor: ToolWindowAnchor,
                                  parentDisposable: Disposable): ToolWindow {
    return registerToolWindow(id, component, anchor, parentDisposable, false, false)
  }

  override fun registerToolWindow(id: String,
                                  component: JComponent,
                                  anchor: ToolWindowAnchor,
                                  parentDisposable: Disposable,
                                  canWorkInDumbMode: Boolean): ToolWindow {
    return registerToolWindow(id, component, anchor, parentDisposable, canWorkInDumbMode, false)
  }

  override fun registerToolWindow(id: String,
                                  component: JComponent,
                                  anchor: ToolWindowAnchor,
                                  parentDisposable: Disposable,
                                  canWorkInDumbMode: Boolean,
                                  canCloseContents: Boolean): ToolWindow {
    return registerToolWindow(id, component, anchor, false, canCloseContents, canWorkInDumbMode, true)
  }

  override fun registerToolWindow(id: String,
                                  canCloseContent: Boolean,
                                  anchor: ToolWindowAnchor): ToolWindow {
    return registerToolWindow(id, null, anchor, false, canCloseContent, false, true)
  }

  override fun registerToolWindow(id: String,
                                  canCloseContent: Boolean,
                                  anchor: ToolWindowAnchor,
                                  secondary: Boolean): ToolWindow {
    return registerToolWindow(id, null, anchor, secondary, canCloseContent, false, true)
  }

  override fun registerToolWindow(id: String,
                                  canCloseContent: Boolean,
                                  anchor: ToolWindowAnchor,
                                  parentDisposable: Disposable,
                                  canWorkInDumbMode: Boolean): ToolWindow {
    return registerToolWindow(id, canCloseContent, anchor, parentDisposable, canWorkInDumbMode, false)
  }

  override fun registerToolWindow(id: String,
                                  canCloseContent: Boolean,
                                  anchor: ToolWindowAnchor,
                                  parentDisposable: Disposable,
                                  canWorkInDumbMode: Boolean,
                                  secondary: Boolean): ToolWindow {
    return registerToolWindow(id, null, anchor, secondary, canCloseContent, canWorkInDumbMode, true)
  }

  private fun registerToolWindow(id: String,
                                 component: JComponent?,
                                 anchor: ToolWindowAnchor,
                                 sideTool: Boolean,
                                 canCloseContent: Boolean,
                                 canWorkInDumbMode: Boolean,
                                 shouldBeAvailable: Boolean): ToolWindow {
    init()
    if (LOG.isDebugEnabled) {
      LOG.debug("enter: installToolWindow($id,$component,$anchor\")")
    }
    ApplicationManager.getApplication().assertIsDispatchThread()
    val existingInfo = myLayout.getInfo(id, false)
    if (existingInfo != null && existingInfo.isRegistered) {
      throw IllegalArgumentException("window with id=\"$id\" is already registered")
    }
    val info = myLayout.register(id, anchor, sideTool)
    val wasActive = info.isActive
    val wasVisible = info.isVisible
    info.isActive = false
    info.isVisible = false
    val disposable = Disposer.newDisposable(id)
    Disposer.register(project, disposable)
    val toolWindow = ToolWindowImpl(this, id, canCloseContent, component, disposable)
    toolWindow.setAvailable(shouldBeAvailable, null)
    ActivateToolWindowAction.ensureToolWindowActionRegistered(toolWindow)
    // create decorator
    val decorator = InternalDecorator(project, info.copy(), toolWindow, canWorkInDumbMode, disposable)
    decorator.addInternalDecoratorListener(myInternalDecoratorListener)
    toolWindow.addPropertyChangeListener(myToolWindowPropertyChangeListener)
    // create and show tool button
    val button = StripeButton(decorator, (myToolWindowPane)!!)
    idToEntry[id] = ToolWindowEntry(button, decorator, ToolWindowFocusWatcher(toolWindow), disposable)
    val commandsList: MutableList<FinalizableCommand> = ArrayList()
    appendAddButtonCmd(button, info, commandsList)
    // If preloaded info is visible or active then we have to show/activate the installed
    // tool window. This step has sense only for windows which are not in the auto hide
    // mode. But if tool window was active but its mode doesn't allow to activate it again
    // (for example, tool window is in auto hide mode) then we just activate editor component.
    if (!info.isAutoHide && (info.isDocked || info.isFloating)) {
      if (wasActive) {
        activateToolWindowImpl((Objects.requireNonNull(info.id))!!, commandsList, true, true)
      }
      else if (wasVisible) {
        showToolWindowImpl((Objects.requireNonNull(info.id))!!, false, commandsList)
      }
    }
    execute(commandsList)
    fireToolWindowRegistered(id)
    return toolWindow
  }

  override fun unregisterToolWindow(id: String) {
    val window = getToolWindow(id) as ToolWindowImpl?
    if (window != null) {
      doUnregisterToolWindow(window.id)
    }
  }

  fun doUnregisterToolWindow(id: String) {
    if (LOG.isDebugEnabled) {
      LOG.debug("enter: unregisterToolWindow($id)")
    }
    ApplicationManager.getApplication().assertIsDispatchThread()
    val info = myLayout.getInfo(id, false)
    if (info == null || !info.isRegistered) {
      return
    }
    val toolWindow = getToolWindow(id) as ToolWindowEx?
    assert(toolWindow != null)
    // remove decorator and tool button from the screen
    val commandsList: MutableList<FinalizableCommand> = ArrayList()
    appendRemoveDecoratorCommand(info, false, commandsList)
    // Save recent appearance of tool window
    myLayout.unregister(id)
    myActiveStack.remove(id, true)
    if (isStackEnabled) {
      mySideStack.remove(id)
    }
    appendRemoveButtonCmd(id, info, commandsList)
    appendApplyWindowInfoCmd(info, commandsList)
    execute(commandsList, false)
    if (!project.isDisposed) {
      project.messageBus.syncPublisher(ToolWindowManagerListener.TOPIC).toolWindowUnregistered(id, (toolWindow)!!)
    }
    // remove all references on tool window and save its last properties
    toolWindow!!.removePropertyChangeListener(myToolWindowPropertyChangeListener)
    // destroy stripe button
    val entry = idToEntry.remove(id)
    Disposer.dispose(entry!!.disposable)
    entry.watcher.deinstall()
  }

  private fun appendRemoveDecoratorCommand(info: WindowInfoImpl,
                                           dirtyMode: Boolean,
                                           commandsList: MutableList<in FinalizableCommand>) {
    if (!info.isVisible) {
      return
    }
    info.isVisible = false
    if (info.isFloating) {
      commandsList.add(RemoveFloatingDecoratorCmd(info))
    }
    else if (info.isWindowed) {
      commandsList.add(RemoveWindowedDecoratorCmd(info))
    }
    else { // docked and sliding windows
      appendRemoveDecoratorCmd((Objects.requireNonNull(info.id))!!, dirtyMode, commandsList)
    }
  }

  override fun getLayout(): DesktopLayout {
    ApplicationManager.getApplication().assertIsDispatchThread()
    return myLayout
  }

  override fun setLayoutToRestoreLater(layout: DesktopLayout) {
    myLayoutToRestoreLater = layout
  }

  override fun getLayoutToRestoreLater(): DesktopLayout {
    return (myLayoutToRestoreLater)!!
  }

  override fun setLayout(layout: DesktopLayout) {
    ApplicationManager.getApplication().assertIsDispatchThread()
    // hide tool window that are invisible or its info is not presented in new layout
    val currentInfos = myLayout.infos
    if (currentInfos.isEmpty()) {
      myLayout = layout
      return
    }
    val commandList: MutableList<FinalizableCommand> = ArrayList()
    for (currentInfo: WindowInfoImpl in currentInfos) {
      val info = layout.getInfo((Objects.requireNonNull(currentInfo.id))!!, false)
      if (currentInfo.isVisible && (info == null || !info.isVisible)) {
        deactivateToolWindowImpl(currentInfo, true, commandList)
      }
    }
    // change anchor of tool windows
    for (currentInfo: WindowInfoImpl in currentInfos) {
      val info = layout.getInfo((Objects.requireNonNull(currentInfo.id))!!, false) ?: continue
      if (currentInfo.anchor != info.anchor || currentInfo.order != info.order) {
        setToolWindowAnchorImpl((currentInfo.id)!!, info.anchor, info.order, commandList)
      }
    }
    // change types of tool windows
    for (currentInfo: WindowInfoImpl in currentInfos) {
      val info = layout.getInfo((Objects.requireNonNull(currentInfo.id))!!, false) ?: continue
      if (currentInfo.type != info.type) {
        setToolWindowTypeImpl((currentInfo.id)!!, info.type, commandList)
      }
    }
    // change other properties
    for (currentInfo: WindowInfoImpl in currentInfos) {
      val info = layout.getInfo((Objects.requireNonNull(currentInfo.id))!!, false) ?: continue
      copyWindowOptions(info, commandList)
    }
    // restore visibility
    for (currentInfo: WindowInfoImpl in currentInfos) {
      val info = layout.getInfo((Objects.requireNonNull(currentInfo.id))!!, false) ?: continue
      if (info.isVisible) {
        showToolWindowImpl((currentInfo.id)!!, false, commandList)
      }
    }
    execute(commandList)
    checkInvariants("")
    myLayout = layout
  }

  override fun invokeLater(runnable: Runnable) {
    val commandList: MutableList<FinalizableCommand> = ArrayList()
    commandList.add(InvokeLaterCmd(runnable, myCommandProcessor))
    execute(commandList)
  }

  override fun getFocusManager(): IdeFocusManager {
    return IdeFocusManager.getInstance(project)
  }

  override fun canShowNotification(toolWindowId: String): Boolean {
    if (!myLayout.isToolWindowRegistered(toolWindowId)) {
      return false
    }
    val stripe = myToolWindowPane!!.getStripeFor(toolWindowId)
    return stripe != null && stripe.getButtonFor(toolWindowId) != null
  }

  override fun notifyByBalloon(toolWindowId: String,
                               type: MessageType,
                               htmlBody: String) {
    notifyByBalloon(toolWindowId, type, htmlBody, null, null)
  }

  override fun notifyByBalloon(toolWindowId: String,
                               type: MessageType,
                               text: String,
                               icon: Icon?,
                               listener: HyperlinkListener?) {
    checkId(toolWindowId)
    val entry = idToEntry[toolWindowId]
    val existing = entry!!.balloon
    if (existing != null) {
      Disposer.dispose(existing)
    }
    val stripe = myToolWindowPane!!.getStripeFor(toolWindowId) ?: return
    val window = getInternalDecorator(toolWindowId).toolWindow
    if (!window.isAvailable) {
      window.isPlaceholderMode = true
      stripe.updatePresentation()
      stripe.revalidate()
      stripe.repaint()
    }
    val anchor = getRegisteredInfoOrLogError(toolWindowId).anchor
    val position = Ref.create(Balloon.Position.below)
    if (ToolWindowAnchor.TOP == anchor) {
      position.set(Balloon.Position.below)
    }
    else if (ToolWindowAnchor.BOTTOM == anchor) {
      position.set(Balloon.Position.above)
    }
    else if (ToolWindowAnchor.LEFT == anchor) {
      position.set(Balloon.Position.atRight)
    }
    else if (ToolWindowAnchor.RIGHT == anchor) {
      position.set(Balloon.Position.atLeft)
    }
    val listenerWrapper = BalloonHyperlinkListener(listener)
    val balloon = JBPopupFactory.getInstance()
      .createHtmlTextBalloonBuilder(text.replace("\n", "<br>"), icon, type.titleForeground, type.popupBackground, listenerWrapper)
      .setBorderColor(type.borderColor).setHideOnClickOutside(false).setHideOnFrameResize(false).createBalloon() as BalloonImpl
    NotificationsManagerImpl.frameActivateBalloonListener(balloon, {
      AppExecutorUtil.getAppScheduledExecutorService().schedule(
        Runnable { balloon.setHideOnClickOutside(true) }, 100,
        TimeUnit.MILLISECONDS)
    }
    )
    listenerWrapper.myBalloon = balloon
    entry.balloon = balloon
    Disposer.register(balloon, Disposable {
      window.setPlaceholderMode(false)
      stripe.updatePresentation()
      stripe.revalidate()
      stripe.repaint()
      entry.balloon = null
    })
    Disposer.register(entry.disposable, balloon)
    execute(listOf(object : FinalizableCommand(null) {
      override fun run() {
        val button = stripe.getButtonFor(toolWindowId)
        LOG.assertTrue(button != null, ("Button was not found, popup won't be shown. Toolwindow id: " +
                                        toolWindowId + ", message: " + text + ", message type: " + type))
        if (button == null) {
          return
        }
        val show = label@ Runnable {
          val tracker: PositionTracker<Balloon>
          if (button.isShowing()) {
            tracker = object : PositionTracker<Balloon?>(button) {
              override fun recalculateLocation(`object`: Balloon): RelativePoint? {
                val twStripe: Stripe? = myToolWindowPane!!.getStripeFor(toolWindowId)
                val twButton: StripeButton? = if (twStripe != null) twStripe.getButtonFor(
                  toolWindowId)
                else null
                if (twButton == null) {
                  return@label null
                }
                if (getToolWindow(toolWindowId)!!.getAnchor() != anchor) {
                  `object`.hide()
                  return@label null
                }
                val point: Point = Point(
                  twButton.getBounds().width / 2, twButton.getHeight() / 2 - 2)
                return@label RelativePoint(twButton, point)
              }
            }
          }
          else {
            tracker = object : PositionTracker<Balloon?>(myToolWindowPane) {
              override fun recalculateLocation(`object`: Balloon): RelativePoint {
                val bounds: Rectangle = myToolWindowPane!!.getBounds()
                val target: Point = StartupUiUtil.getCenterPoint(bounds,
                                                                 Dimension(
                                                                   1, 1))
                if (ToolWindowAnchor.TOP == anchor) {
                  target.y = 0
                }
                else if (ToolWindowAnchor.BOTTOM == anchor) {
                  target.y = bounds.height - 3
                }
                else if (ToolWindowAnchor.LEFT == anchor) {
                  target.x = 0
                }
                else if (ToolWindowAnchor.RIGHT == anchor) {
                  target.x = bounds.width
                }
                return@label RelativePoint((myToolWindowPane)!!,
                                           target)
              }
            }
          }
          if (!balloon.isDisposed()) {
            balloon.show(tracker, position.get())
          }
        }
        if (button.isValid) {
          show.run()
        }
        else {
          SwingUtilities.invokeLater(show)
        }
      }
    }))
  }

  override fun getToolWindowBalloon(id: String): Balloon? {
    val entry = idToEntry[id]
    return entry?.balloon
  }

  override fun isEditorComponentActive(): Boolean {
    ApplicationManager.getApplication().assertIsDispatchThread()
    val owner = focusManager.focusOwner
    val splitters = ComponentUtil.getParentOfType(
      (EditorsSplitters::class.java as Class<out EditorsSplitters?>), owner)
    return splitters != null
  }

  fun getToolWindowAnchor(id: String): ToolWindowAnchor {
    return getRegisteredInfoOrLogError(id).anchor
  }

  fun setToolWindowAnchor(id: String, anchor: ToolWindowAnchor) {
    ApplicationManager.getApplication().assertIsDispatchThread()
    setToolWindowAnchor(id, anchor, -1)
  }

  fun setToolWindowAnchor(id: String, anchor: ToolWindowAnchor, order: Int) {
    ApplicationManager.getApplication().assertIsDispatchThread()
    val commandList: MutableList<FinalizableCommand> = ArrayList()
    setToolWindowAnchorImpl(id, anchor, order, commandList)
    execute(commandList)
  }

  private fun setToolWindowAnchorImpl(id: String,
                                      anchor: ToolWindowAnchor,
                                      order: Int,
                                      commandsList: MutableList<in FinalizableCommand>) {
    val info = getRegisteredInfoOrLogError(id)
    if (anchor == info.anchor && order == info.order) {
      return
    }
    // if tool window isn't visible or only order number is changed then just remove/add stripe button
    if (!info.isVisible || (anchor == info.anchor) || info.isFloating || info.isWindowed) {
      appendRemoveButtonCmd(id, info, commandsList)
      myLayout.setAnchor(id, anchor, order)
      // update infos for all window. Actually we have to update only infos affected by
      // setAnchor method
      for (info1: WindowInfoImpl in myLayout.infos) {
        appendApplyWindowInfoCmd(info1, commandsList)
      }
      appendAddButtonCmd(getStripeButton(id), info, commandsList)
    }
    else { // for docked and sliding windows we have to move buttons and window's decorators
      info.isVisible = false
      appendRemoveDecoratorCmd(id, false, commandsList)
      appendRemoveButtonCmd(id, info, commandsList)
      myLayout.setAnchor(id, anchor, order)
      // update infos for all window. Actually we have to update only infos affected by
      // setAnchor method
      for (info1: WindowInfoImpl in myLayout.infos) {
        appendApplyWindowInfoCmd(info1, commandsList)
      }
      appendAddButtonCmd(getStripeButton(id), info, commandsList)
      showToolWindowImpl(id, false, commandsList)
      if (info.isActive) {
        appendRequestFocusInToolWindowCmd(id, commandsList)
      }
    }
  }

  fun isSplitMode(id: String): Boolean {
    ApplicationManager.getApplication().assertIsDispatchThread()
    return getRegisteredInfoOrLogError(id).isSplit
  }

  fun getContentUiType(id: String): ToolWindowContentUiType {
    ApplicationManager.getApplication().assertIsDispatchThread()
    return getRegisteredInfoOrLogError(id).contentUiType
  }

  fun setSideTool(id: String, isSide: Boolean) {
    val commandList: MutableList<FinalizableCommand> = ArrayList()
    setSplitModeImpl(id, isSide, commandList)
    execute(commandList)
  }

  fun setContentUiType(id: String, type: ToolWindowContentUiType) {
    val info = getRegisteredInfoOrLogError(id)
    info.contentUiType = type
    val commandList: MutableList<FinalizableCommand> = ArrayList()
    appendApplyWindowInfoCmd(info, commandList)
    execute(commandList)
  }

  fun setSideToolAndAnchor(id: String, anchor: ToolWindowAnchor, order: Int, isSide: Boolean) {
    hideToolWindow(id, false)
    myLayout.setSplitMode(id, isSide)
    setToolWindowAnchor(id, anchor, order)
    activateToolWindow(id, false, false)
  }

  private fun setSplitModeImpl(id: String, isSplit: Boolean, commandList: MutableList<in FinalizableCommand>) {
    val info = getRegisteredInfoOrLogError(id)
    if (isSplit == info.isSplit) {
      return
    }
    myLayout.setSplitMode(id, isSplit)
    val wasActive = info.isActive
    val wasVisible = info.isVisible
    // We should hide the window and show it in a 'new place' to automatically hide possible window that is already located in a 'new place'
    if (wasActive || wasVisible) {
      hideToolWindow(id, false)
    }
    for (info1: WindowInfoImpl in myLayout.infos) {
      appendApplyWindowInfoCmd(info1, commandList)
    }
    if (wasVisible || wasActive) {
      showToolWindowImpl(id, true, commandList)
    }
    if (wasActive) {
      activateToolWindowImpl(id, commandList, true, true)
    }
    commandList.add(myToolWindowPane!!.createUpdateButtonPositionCmd(id, myCommandProcessor))
  }

  fun getToolWindowInternalType(id: String): ToolWindowType {
    ApplicationManager.getApplication().assertIsDispatchThread()
    return getRegisteredInfoOrLogError(id).internalType
  }

  fun getToolWindowType(id: String): ToolWindowType {
    return getRegisteredInfoOrLogError(id).type
  }

  private fun fireToolWindowRegistered(id: String) {
    project.messageBus.syncPublisher(ToolWindowManagerListener.TOPIC).toolWindowRegistered(id)
  }

  protected open fun fireStateChanged() {
    project.messageBus.syncPublisher(ToolWindowManagerListener.TOPIC).stateChanged()
  }

  fun isToolWindowActive(id: String): Boolean {
    ApplicationManager.getApplication().assertIsDispatchThread()
    return getRegisteredInfoOrLogError(id).isActive
  }

  fun isToolWindowAutoHide(id: String): Boolean {
    ApplicationManager.getApplication().assertIsDispatchThread()
    return getRegisteredInfoOrLogError(id).isAutoHide
  }

  fun isToolWindowVisible(id: String): Boolean {
    return getRegisteredInfoOrLogError(id).isVisible
  }

  fun setToolWindowAutoHide(id: String, autoHide: Boolean) {
    ApplicationManager.getApplication().assertIsDispatchThread()
    val commandList: MutableList<FinalizableCommand> = ArrayList()
    setToolWindowAutoHideImpl(id, autoHide, commandList)
    execute(commandList)
  }

  private fun setToolWindowAutoHideImpl(id: String,
                                        autoHide: Boolean,
                                        commandsList: MutableList<in FinalizableCommand>) {
    val info = getRegisteredInfoOrLogError(id)
    if (info.isAutoHide == autoHide) {
      return
    }
    info.isAutoHide = autoHide
    appendApplyWindowInfoCmd(info, commandsList)
    if (info.isVisible) {
      deactivateWindows(id, commandsList)
      showAndActivate(id, false, commandsList, true)
    }
  }

  private fun copyWindowOptions(origin: WindowInfoImpl, commandsList: MutableList<in FinalizableCommand>) {
    val id = (Objects.requireNonNull(origin.id))!!
    val info = getRegisteredInfoOrLogError(id)
    var changed = false
    if (info.isAutoHide != origin.isAutoHide) {
      info.isAutoHide = origin.isAutoHide
      changed = true
    }
    if (info.weight != origin.weight) {
      info.weight = origin.weight
      changed = true
    }
    if (info.sideWeight != origin.sideWeight) {
      info.sideWeight = origin.sideWeight
      changed = true
    }
    if (info.contentUiType !== origin.contentUiType) {
      info.contentUiType = origin.contentUiType
      changed = true
    }
    if (changed) {
      appendApplyWindowInfoCmd(info, commandsList)
      if (info.isVisible) {
        deactivateWindows(id, commandsList)
        showAndActivate(id, false, commandsList, true)
      }
    }
  }

  fun setToolWindowType(id: String, type: ToolWindowType) {
    ApplicationManager.getApplication().assertIsDispatchThread()
    val commandList: MutableList<FinalizableCommand> = ArrayList()
    setToolWindowTypeImpl(id, type, commandList)
    execute(commandList)
  }

  private fun setToolWindowTypeImpl(id: String,
                                    type: ToolWindowType,
                                    commandsList: MutableList<in FinalizableCommand>) {
    val info = getRegisteredInfoOrLogError(id)
    if (info.type == type) {
      return
    }
    if (info.isVisible) {
      val dirtyMode = info.isDocked || info.isSliding
      appendRemoveDecoratorCommand(info, dirtyMode, commandsList)
      info.type = type
      appendApplyWindowInfoCmd(info, commandsList)
      deactivateWindows(id, commandsList)
      showAndActivate(id, dirtyMode, commandsList, true)
      appendUpdateRootPaneCmd(commandsList)
    }
    else {
      info.type = type
      appendApplyWindowInfoCmd(info, commandsList)
    }
  }

  private fun appendApplyWindowInfoCmd(info: WindowInfoImpl, commandsList: MutableList<in FinalizableCommand>) {
    val button = getStripeButton((Objects.requireNonNull(info.id))!!)
    val decorator = getInternalDecorator((info.id)!!)
    commandsList.add(ApplyWindowInfoCmd(info, button, decorator, myCommandProcessor))
  }

  /**
   * @see ToolWindowsPane.createAddDecoratorCmd
   */
  private fun appendAddDecoratorCmd(decorator: InternalDecorator,
                                    info: WindowInfoImpl,
                                    dirtyMode: Boolean,
                                    commandsList: MutableList<in FinalizableCommand>) {
    commandsList.add(myToolWindowPane!!.createAddDecoratorCmd(decorator, info, dirtyMode, myCommandProcessor))
  }

  /**
   * @see ToolWindowsPane.createRemoveDecoratorCmd
   */
  private fun appendRemoveDecoratorCmd(id: String,
                                       dirtyMode: Boolean,
                                       commandsList: MutableList<in FinalizableCommand>) {
    commandsList.add(myToolWindowPane!!.createRemoveDecoratorCmd(id, dirtyMode, myCommandProcessor))
    commandsList.add(myToolWindowPane!!.createTransferFocusCmd(
      BooleanSupplier { commandsList.stream().anyMatch({ c: FinalizableCommand? -> c is RequestFocusInToolWindowCmd }) },
      myCommandProcessor))
  }

  /**
   * @see ToolWindowsPane.createAddButtonCmd
   */
  private fun appendAddButtonCmd(button: StripeButton,
                                 info: WindowInfoImpl,
                                 commandsList: MutableList<in FinalizableCommand>) {
    val comparator = myLayout.comparator(info.anchor)
    commandsList.add(myToolWindowPane!!.createAddButtonCmd(button, info, comparator, myCommandProcessor))
  }

  /**
   * @see ToolWindowsPane.createAddButtonCmd
   */
  private fun appendRemoveButtonCmd(id: String,
                                    info: WindowInfoImpl,
                                    commandsList: MutableList<in FinalizableCommand>) {
    val cmd = myToolWindowPane!!.createRemoveButtonCmd(info, id, myCommandProcessor)
    if (cmd != null) {
      commandsList.add(cmd)
    }
  }

  private fun appendRequestFocusInToolWindowCmd(id: String, commandList: MutableList<in FinalizableCommand>) {
    if (!myLayout.isToolWindowRegistered(id)) {
      return
    }
    val entry = idToEntry.get(id) ?: return
    val focusWatcher = entry.watcher
    commandList.add(RequestFocusInToolWindowCmd(entry.internalDecorator.toolWindow, focusWatcher, myCommandProcessor, project))
  }

  /**
   * @see ToolWindowsPane.createSetEditorComponentCmd
   */
  private fun appendSetEditorComponentCmd(component: JComponent?, commandsList: MutableList<in FinalizableCommand>) {
    commandsList.add(myToolWindowPane!!.createSetEditorComponentCmd(component, myCommandProcessor))
  }

  private fun appendUpdateRootPaneCmd(commandsList: MutableList<in FinalizableCommand>) {
    val frame = myFrame
    commandsList.add(object : FinalizableCommand(myCommandProcessor) {
      override fun run() {
        try {
          val rootPane = frame!!.rootPane
          if (rootPane != null) {
            rootPane.revalidate()
            rootPane.repaint()
          }
        }
        finally {
          finish()
        }
      }
    })
  }

  override fun clearSideStack() {
    if (isStackEnabled) {
      mySideStack.clear()
    }
  }

  override fun getState(): Element? {
    if (myFrame == null) { // do nothing if the project was not opened
      return null
    }
    // update size of all open floating windows. See SCR #18439
    for (info: WindowInfoImpl in myLayout.infos) {
      if (info.isVisible) {
        getInternalDecorator((Objects.requireNonNull(info.id))!!).fireResized()
      }
    }
    val element = Element("state")
    // Save frame's bounds
    // [tav] Where we load these bounds? Should we just remove this code? (because we load frame bounds in WindowManagerImpl.allocateFrame)
    // Anyway, we should save bounds in device space to preserve backward compatibility with the IDE-managed HiDPI mode (see JBUI.ScaleType).
    // However, I won't change this code because I can't even test it.
    val frameBounds = myFrame!!.frame.bounds
    val frameElement = Element(FRAME_ELEMENT)
    element.addContent(frameElement)
    frameElement.setAttribute(X_ATTR, Integer.toString(frameBounds.x))
    frameElement.setAttribute(Y_ATTR, Integer.toString(frameBounds.y))
    frameElement.setAttribute(WIDTH_ATTR, Integer.toString(frameBounds.width))
    frameElement.setAttribute(HEIGHT_ATTR, Integer.toString(frameBounds.height))
    frameElement.setAttribute(EXTENDED_STATE_ATTR,
                              Integer.toString(myFrame!!.frame.extendedState))
    // Save whether editor is active or not
    if (isEditorComponentActive) {
      val editorElement = Element(EDITOR_ELEMENT)
      editorElement.setAttribute(ACTIVE_ATTR_VALUE, "true")
      element.addContent(editorElement)
    }
    // Save layout of tool windows
    val layoutElement = myLayout.writeExternal(DesktopLayout.TAG)
    if (layoutElement != null) {
      element.addContent(layoutElement)
    }
    val layoutToRestoreElement = if (myLayoutToRestoreLater == null) null
    else myLayoutToRestoreLater!!.writeExternal(LAYOUT_TO_RESTORE)
    if (layoutToRestoreElement != null) {
      element.addContent(layoutToRestoreElement)
    }
    return element
  }

  private val pendingSetLayoutTask = AtomicReference<Runnable?>()
  override fun loadState(state: Element) {
    for (e: Element in state.children) {
      if ((DesktopLayout.TAG == e.name)) {
        val layout = DesktopLayout()
        layout.readExternal(e)
        val task = Runnable {
          myLayout.copyNotRegisteredFrom(layout)
          setLayout(layout)
        }
        val app = ApplicationManager.getApplication()
        if (app.isDispatchThread) {
          pendingSetLayoutTask.set(null)
          task.run()
        }
        else {
          pendingSetLayoutTask.set(task)
          app.invokeLater(Runnable {
            val effectiveTask: Runnable? = pendingSetLayoutTask.getAndSet(null)
            if (effectiveTask != null) {
              effectiveTask.run()
            }
          }, project.disposed)
        }
      }
      else if ((LAYOUT_TO_RESTORE == e.name)) {
        myLayoutToRestoreLater = DesktopLayout()
        myLayoutToRestoreLater!!.readExternal(e)
      }
    }
    checkInvariants("")
  }

  fun setDefaultState(toolWindow: ToolWindowImpl,
                      anchor: ToolWindowAnchor?,
                      type: ToolWindowType?,
                      floatingBounds: Rectangle?) {
    val info = getRegisteredInfoOrLogError(toolWindow.id)
    if (info.isWasRead) return
    if (floatingBounds != null) {
      info.floatingBounds = floatingBounds
    }
    if (anchor != null) {
      toolWindow.setAnchor(anchor, null)
    }
    if (type != null) {
      toolWindow.setType(type, null)
    }
  }

  fun setDefaultContentUiType(toolWindow: ToolWindowImpl, type: ToolWindowContentUiType) {
    val info = getRegisteredInfoOrLogError(toolWindow.id)
    if (info.isWasRead) {
      return
    }
    toolWindow.setContentUiType(type, null)
  }

  fun stretchWidth(toolWindow: ToolWindowImpl, value: Int) {
    myToolWindowPane!!.stretchWidth(toolWindow, value)
  }

  override fun isMaximized(wnd: ToolWindow): Boolean {
    return myToolWindowPane!!.isMaximized(wnd)
  }

  override fun setMaximized(window: ToolWindow, maximized: Boolean) {
    if (window.type == ToolWindowType.FLOATING && window is ToolWindowImpl) {
      MaximizeActiveDialogAction.doMaximize(getFloatingDecorator(window.id))
      return
    }
    if (window.type == ToolWindowType.WINDOWED && window is ToolWindowImpl) {
      val decorator = getWindowedDecorator(window.id)
      val frame = if (decorator != null && decorator.frame is Frame) decorator.frame as Frame else null
      if (frame != null) {
        val state = frame.state
        if (state == Frame.NORMAL) {
          frame.state = Frame.MAXIMIZED_BOTH
        }
        else if (state == Frame.MAXIMIZED_BOTH) {
          frame.state = Frame.NORMAL
        }
      }
      return
    }
    myToolWindowPane!!.setMaximized(window, maximized)
  }

  fun stretchHeight(toolWindow: ToolWindowImpl?, value: Int) {
    myToolWindowPane!!.stretchHeight((toolWindow)!!, value)
  }

  private class BalloonHyperlinkListener internal constructor(private val myListener: HyperlinkListener?) : HyperlinkListener {
    val myBalloon: Balloon? = null
    override fun hyperlinkUpdate(e: HyperlinkEvent) {
      if (myBalloon != null && e.eventType == HyperlinkEvent.EventType.ACTIVATED) {
        myBalloon.hide()
      }
      myListener?.hyperlinkUpdate(e)
    }

  }

  /**
   * This command creates and shows `FloatingDecorator`.
   */
  private inner class AddFloatingDecoratorCmd(entry: ToolWindowEntry, info: WindowInfoImpl) : FinalizableCommand(
    myCommandProcessor) {
    private val myFloatingDecorator: FloatingDecorator
    override fun run() {
      try {
        myFloatingDecorator.show()
      }
      finally {
        finish()
      }
    }

    /**
     * Creates floating decorator for specified internal decorator.
     */
    init {
      val frame = myFrame!!.frame
      val decorator = entry.internalDecorator
      myFloatingDecorator = FloatingDecorator(frame, info.copy(), decorator)
      entry.floatingDecorator = myFloatingDecorator
      val bounds = info.floatingBounds
      if (((bounds != null) && (
          bounds.width > 0) && (
             bounds.height > 0) &&
           WindowManager.getInstance().isInsideScreenBounds(bounds.x, bounds.y, bounds.width))) {
        myFloatingDecorator.bounds = Rectangle(bounds)
      }
      else { // place new frame at the center of main frame if there are no floating bounds
        var size = decorator.size
        if (size.width == 0 || size.height == 0) {
          size = decorator.preferredSize
        }
        myFloatingDecorator.size = size
        myFloatingDecorator.setLocationRelativeTo(frame)
      }
    }
  }

  /**
   * This command hides and destroys floating decorator for tool window
   * with specified `ID`.
   */
  private inner class RemoveFloatingDecoratorCmd(info: WindowInfoImpl) : FinalizableCommand(myCommandProcessor) {
    private val myFloatingDecorator: FloatingDecorator?
    override fun run() {
      try {
        myFloatingDecorator!!.dispose()
      }
      finally {
        finish()
      }
    }

    override fun getExpireCondition(): BooleanSupplier {
      return BooleanSupplier { ApplicationManager.getApplication().isDisposed() }
    }

    init {
      myFloatingDecorator = getFloatingDecorator((Objects.requireNonNull(info.id))!!)
      info.floatingBounds = Objects.requireNonNull(myFloatingDecorator)!!.bounds
    }
  }

  /**
   * This command creates and shows `WindowedDecorator`.
   */
  private inner class AddWindowedDecoratorCmd(entry: ToolWindowEntry, info: WindowInfoImpl) : FinalizableCommand(
    myCommandProcessor) {
    private val myWindowedDecorator: WindowedDecorator
    private val myShouldBeMaximized: Boolean
    override fun run() {
      try {
        myWindowedDecorator.show(false)
        val window = myWindowedDecorator.frame
        val rootPane = (window as RootPaneContainer).rootPane
        val rootPaneBounds = rootPane.bounds
        val point = rootPane.locationOnScreen
        val windowBounds = window.bounds
        //Point windowLocation = windowBounds.getLocation();
        //windowLocation.translate(windowLocation.x - point.x, windowLocation.y - point.y);
        window.setLocation(2 * windowBounds.x - point.x, 2 * windowBounds.y - point.y)
        window.setSize(2 * windowBounds.width - rootPaneBounds.width, 2 * windowBounds.height - rootPaneBounds.height)
        if (myShouldBeMaximized && window is Frame) {
          (window as Frame).extendedState = Frame.MAXIMIZED_BOTH
        }
        window.toFront()
      }
      finally {
        finish()
      }
    }

    /**
     * Creates windowed decorator for specified internal decorator.
     */
    init {
      val decorator = entry.internalDecorator
      myWindowedDecorator = WindowedDecorator(project, info.copy(), decorator)
      myShouldBeMaximized = info.isMaximized
      val window = myWindowedDecorator.frame
      val bounds = info.floatingBounds
      if (((bounds != null) && (
          bounds.width > 0) && (
             bounds.height > 0) &&
           WindowManager.getInstance().isInsideScreenBounds(bounds.x, bounds.y, bounds.width))) {
        window.bounds = Rectangle(bounds)
      }
      else { // place new frame at the center of main frame if there are no floating bounds
        var size = decorator.size
        if (size.width == 0 || size.height == 0) {
          size = decorator.preferredSize
        }
        window.size = size
        window.setLocationRelativeTo(myFrame!!.frame)
      }
      entry.windowedDecorator = myWindowedDecorator
      myWindowedDecorator.addDisposable({
                                          val e: ToolWindowEntry? = idToEntry.get(info.id)
                                          if (e != null && e.windowedDecorator != null) {
                                            hideToolWindow(
                                              (Objects.requireNonNull(info.id))!!,
                                              false)
                                          }
                                        })
    }
  }

  /**
   * This command hides and destroys floating decorator for tool window
   * with specified `ID`.
   */
  private inner class RemoveWindowedDecoratorCmd(info: WindowInfoImpl) : FinalizableCommand(myCommandProcessor) {
    private val myWindowedDecorator: WindowedDecorator?
    override fun run() {
      try {
        Disposer.dispose((myWindowedDecorator)!!)
      }
      finally {
        finish()
      }
    }

    override fun getExpireCondition(): BooleanSupplier {
      return BooleanSupplier { ApplicationManager.getApplication().isDisposed() }
    }

    init {
      myWindowedDecorator = getWindowedDecorator((Objects.requireNonNull(info.id))!!)
      val entry = idToEntry[info.id]
      if (entry != null) {
        entry.windowedDecorator = null
      }
      val frame = Objects.requireNonNull(myWindowedDecorator)!!.frame
      if (!frame.isShowing) {
        return
      }
      val maximized = (frame as JFrame).extendedState == Frame.MAXIMIZED_BOTH
      if (maximized) {
        frame.extendedState = Frame.NORMAL
        frame.invalidate()
        frame.revalidate()
      }
      val bounds = getRootBounds(frame)
      info.floatingBounds = bounds
      info.isMaximized = maximized
    }
  }

  /**
   * Notifies window manager about focus traversal in tool window
   */
  internal class ToolWindowFocusWatcher(toolWindow: ToolWindowImpl) : FocusWatcher() {
    private val myId: String
    private val myToolWindow: ToolWindowImpl
    fun deinstall() {
      deinstall(myToolWindow.component)
    }

    override fun isFocusedComponentChangeValid(comp: Component, cause: AWTEvent): Boolean {
      return myToolWindow.toolWindowManager.myCommandProcessor.commandCount == 0 && comp != null
    }

    override fun focusedComponentChanged(component: Component, cause: AWTEvent?) {
      if (myToolWindow.toolWindowManager.myCommandProcessor.commandCount > 0 || component == null) {
        return
      }
      val info = myToolWindow.toolWindowManager.getRegisteredInfoOrLogError(myId)
      if (!info.isActive) {
        getFocusManagerImpl(
          myToolWindow.toolWindowManager.myProject).doWhenFocusSettlesDown(object : EdtRunnable() {
          override fun runEdt() {
            val windowInfo = myToolWindow.toolWindowManager.myLayout.getInfo(myId, true)
            if (windowInfo == null || !windowInfo.isVisible) return
            myToolWindow.toolWindowManager.activateToolWindow(myId, false, false)
          }
        })
      }
    }

    init {
      myId = toolWindow.id
      install(toolWindow.component)
      myToolWindow = toolWindow
    }
  }

  /**
   * Spies on IdeToolWindow properties and applies them to the window
   * state.
   */
  private inner class MyToolWindowPropertyChangeListener() : PropertyChangeListener {
    override fun propertyChange(e: PropertyChangeEvent) {
      val toolWindow = e.source as ToolWindowImpl
      if ((ToolWindowEx.PROP_AVAILABLE == e.propertyName)) {
        val info = getRegisteredInfoOrLogError(toolWindow.id)
        if (!toolWindow.isAvailable && info.isVisible) {
          hideToolWindow(toolWindow.id, false)
        }
      }
      val entry = idToEntry[toolWindow.id]
      entry?.stripeButton?.updatePresentation()
      ActivateToolWindowAction.updateToolWindowActionPresentation(toolWindow)
    }
  }

  /**
   * Translates events from InternalDecorator into ToolWindowManager method invocations.
   */
  private inner class MyInternalDecoratorListener() : InternalDecoratorListener {
    override fun anchorChanged(source: InternalDecorator, anchor: ToolWindowAnchor) {
      setToolWindowAnchor(source.toolWindow.id, anchor)
    }

    override fun autoHideChanged(source: InternalDecorator, autoHide: Boolean) {
      setToolWindowAutoHide(source.toolWindow.id, autoHide)
    }

    override fun hidden(source: InternalDecorator) {
      hideToolWindow(source.toolWindow.id, false)
    }

    override fun hiddenSide(source: InternalDecorator) {
      hideToolWindow(source.toolWindow.id, true)
    }

    override fun contentUiTypeChanges(source: InternalDecorator, type: ToolWindowContentUiType) {
      setContentUiType(source.toolWindow.id, type)
    }

    /**
     * Handles event from decorator and modify weight/floating bounds of the
     * tool window depending on decoration type.
     */
    override fun resized(source: InternalDecorator) {
      if (!source.isShowing) {
        return  // do not recalculate the tool window size if it is not yet shown (and, therefore, has 0,0,0,0 bounds)
      }
      val info = getRegisteredInfoOrLogError(source.toolWindow.id)
      if (info.isFloating) {
        val owner = SwingUtilities.getWindowAncestor(source)
        if (owner != null) {
          info.floatingBounds = owner.bounds
        }
      }
      else if (info.isWindowed) {
        val decorator = getWindowedDecorator((Objects.requireNonNull(info.id))!!)
        val frame = decorator?.frame
        if (frame == null || !frame.isShowing) return
        info.floatingBounds = getRootBounds(frame as JFrame)
        info.isMaximized = frame.extendedState == Frame.MAXIMIZED_BOTH
      }
      else { // docked and sliding windows
        val anchor = info.anchor
        var another: InternalDecorator? = null
        if (source.parent is Splitter) {
          var sizeInSplit = if (anchor.isSplitVertically) source.height.toFloat() else source.width.toFloat()
          val splitter = source.parent as Splitter
          if (splitter.secondComponent === source) {
            sizeInSplit += splitter.dividerWidth.toFloat()
            another = splitter.firstComponent as InternalDecorator
          }
          else {
            another = splitter.secondComponent as InternalDecorator
          }
          if (anchor.isSplitVertically) {
            info.sideWeight = sizeInSplit / splitter.height
          }
          else {
            info.sideWeight = sizeInSplit / splitter.width
          }
        }
        var paneWeight = if (anchor.isHorizontal) source.height.toFloat() / myToolWindowPane!!.myLayeredPane.height else source.width.toFloat() / myToolWindowPane!!.myLayeredPane.width
        info.weight = paneWeight
        if (another != null && anchor.isSplitVertically) {
          paneWeight = if (anchor.isHorizontal) another.height.toFloat() / myToolWindowPane!!.myLayeredPane.height else another.width.toFloat() / myToolWindowPane!!.myLayeredPane.width
          another.windowInfo.weight = paneWeight
        }
      }
    }

    override fun activated(source: InternalDecorator) {
      activateToolWindow(source.toolWindow.id, true, true)
    }

    override fun typeChanged(source: InternalDecorator, type: ToolWindowType) {
      setToolWindowType(source.toolWindow.id, type)
    }

    override fun sideStatusChanged(source: InternalDecorator, isSideTool: Boolean) {
      setSideTool(source.toolWindow.id, isSideTool)
    }

    override fun visibleStripeButtonChanged(source: InternalDecorator, visible: Boolean) {
      setShowStripeButton(source.toolWindow.id, visible)
    }
  }

  override fun fallbackToEditor(): Boolean {
    return myActiveStack.isEmpty
  }

  private fun focusToolWindowByDefault(idToIgnore: String?) {
    var toFocus: String? = null
    for (each: String in myActiveStack.stack) {
      if (idToIgnore != null && idToIgnore.equals(each, ignoreCase = true)) continue
      if (getRegisteredInfoOrLogError(each).isVisible) {
        toFocus = each
        break
      }
    }
    if (toFocus == null) {
      for (each: String in myActiveStack.persistentStack) {
        if (idToIgnore != null && idToIgnore.equals(each, ignoreCase = true)) continue
        if (getRegisteredInfoOrLogError(each).isVisible) {
          toFocus = each
          break
        }
      }
    }
    if (toFocus != null && !ApplicationManager.getApplication().isDisposed) {
      activateToolWindow(toFocus, false, true)
    }
  }

  /**
   * Delegate method for compatibility with older versions of IDEA
   */
  fun requestFocus(c: Component, forced: Boolean): ActionCallback {
    return IdeFocusManager.getInstance(project).requestFocus(c, forced)
  }

  fun doWhenFocusSettlesDown(runnable: Runnable) {
    IdeFocusManager.getInstance(project).doWhenFocusSettlesDown(runnable)
  }

  fun setShowStripeButton(id: String, visibleOnPanel: Boolean) {
    val info = getRegisteredInfoOrLogError(id)
    if (visibleOnPanel == info.isShowStripeButton) {
      return
    }
    info.isShowStripeButton = visibleOnPanel
    val commandList: MutableList<FinalizableCommand> = ArrayList()
    appendApplyWindowInfoCmd(info, commandList)
    execute(commandList)
  }

  fun isShowStripeButton(id: String): Boolean {
    val info = myLayout.getInfo(id, true)
    return info == null || info.isShowStripeButton
  }

  class InitToolWindowsActivity() : StartupActivity.DumbAware {
    override fun runActivity(project: Project) {
      val ex = getInstanceEx(project)
      if (ex is ToolWindowManagerImpl) {
        val manager = ex
        val list: MutableList<FinalizableCommand> = ArrayList()
        manager.registerToolWindowsFromBeans(list)
        manager.initAll(list)
        EdtInvocationManager.getInstance().invokeLater({
                                                         manager.execute(list)
                                                         manager.flushCommands()
                                                       })
        ToolWindowEP.EP_NAME.addExtensionPointListener(
          object : ExtensionPointListener<ToolWindowEP?> {
            override fun extensionAdded(extension: ToolWindowEP,
                                        pluginDescriptor: PluginDescriptor) {
              manager.initToolWindow(extension)
            }

            override fun extensionRemoved(extension: ToolWindowEP,
                                          pluginDescriptor: PluginDescriptor) {
              manager.unregisterToolWindow(extension.id)
            }
          }, project)
      }
    }
  }

  private fun checkInvariants(additionalMessage: String) {
    if (!ApplicationManager.getApplication().isEAP && !ApplicationManager.getApplication().isInternal) {
      return
    }
    val violations: MutableList<String> = ArrayList()
    for (info: WindowInfoImpl in myLayout.infos) {
      val id = Objects.requireNonNull(info.id)
      if (info.isVisible) {
        if (info.isFloating) {
          val entry = idToEntry[id]
          if (entry != null && entry.floatingDecorator == null) {
            violations.add("Floating window has no decorator: $id")
          }
        }
        else if (info.isWindowed) {
          val entry = idToEntry[id]
          if (entry != null && entry.windowedDecorator == null) {
            violations.add("Windowed window has no decorator: $id")
          }
        }
      }
    }
    if (!violations.isEmpty()) {
      LOG.error(
        "Invariants failed: \n" + java.lang.String.join("\n", violations) + "\nContext: " + additionalMessage)
    }
  }

  companion object {
    private val LOG = Logger.getInstance(
      ToolWindowManagerImpl::class.java)
    @NonNls
    private val EDITOR_ELEMENT = "editor"
    @NonNls
    private val ACTIVE_ATTR_VALUE = "active"
    @NonNls
    private val FRAME_ELEMENT = "frame"
    @NonNls
    private val X_ATTR = "x"
    @NonNls
    private val Y_ATTR = "y"
    @NonNls
    private val WIDTH_ATTR = "width"
    @NonNls
    private val HEIGHT_ATTR = "height"
    @NonNls
    private val EXTENDED_STATE_ATTR = "extended-state"
    @NonNls
    private val LAYOUT_TO_RESTORE = "layout-to-restore"

    private fun focusDefaultElementInSelectedEditor() {
      EditorsSplitters.findDefaultComponentInSplittersIfPresent(
        { obj: JComponent -> obj.requestFocus() })
    }

    private fun areAllModifiersPressed(@JdkConstants.InputEventMask modifiers: Int, @JdkConstants.InputEventMask mask: Int): Boolean {
      return (modifiers xor mask) == 0
    }

    @JdkConstants.InputEventMask
    private fun keyCodeToInputMask(code: Int): Int {
      var mask = 0
      if (code == KeyEvent.VK_SHIFT) {
        mask = InputEvent.SHIFT_MASK
      }
      if (code == KeyEvent.VK_CONTROL) {
        mask = InputEvent.CTRL_MASK
      }
      if (code == KeyEvent.VK_META) {
        mask = InputEvent.META_MASK
      }
      if (code == KeyEvent.VK_ALT) {
        mask = InputEvent.ALT_MASK
      }
      return mask
    }

    // We should filter out 'mixed' mask like InputEvent.META_MASK | InputEvent.META_DOWN_MASK
    @get:InputEventMask
    private val activateToolWindowVKsMask: Int
      private get() {
        if (ApplicationManager.getApplication() == null) return 0
        val keymap = Objects.requireNonNull(KeymapManager.getInstance()).activeKeymap
        val baseShortcut = keymap.getShortcuts("ActivateProjectToolWindow")
        var baseModifiers = if (SystemInfo.isMac) InputEvent.META_MASK else InputEvent.ALT_MASK
        for (each: Shortcut in baseShortcut) {
          if (each is KeyboardShortcut) {
            val keyStroke = each.firstKeyStroke
            baseModifiers = keyStroke.modifiers
            if (baseModifiers > 0) {
              break
            }
          }
        }
        // We should filter out 'mixed' mask like InputEvent.META_MASK | InputEvent.META_DOWN_MASK
        return baseModifiers and (InputEvent.SHIFT_MASK or InputEvent.CTRL_MASK or InputEvent.META_MASK or InputEvent.ALT_MASK)
      }

    private fun getFocusManagerImpl(project: Project): IdeFocusManager {
      return IdeFocusManager.getInstance(project)
    }

    private fun createEditorComponent(project: Project): JComponent {
      return FrameEditorComponentProvider.EP.extensionList[0].createEditorComponent(project)
    }

    private fun createInitializingLabel(): JLabel {
      val label = JLabel("Initializing...", SwingConstants.CENTER)
      label.isOpaque = true
      val treeBg = UIUtil.getTreeBackground()
      label.background = ColorUtil.toAlpha(treeBg, 180)
      val treeFg = UIUtil.getTreeForeground()
      label.foreground = ColorUtil.toAlpha(treeFg, 180)
      return label
    }

    private fun isToHideOnDeactivation(info: WindowInfoImpl): Boolean {
      return if (info.isFloating || info.isWindowed) false else info.isAutoHide || info.isSliding
    }

    private val isStackEnabled: Boolean
      private get() = Registry.`is`("ide.enable.toolwindow.stack")

    private fun getRootBounds(frame: JFrame): Rectangle {
      val rootPane = frame.rootPane
      val bounds = rootPane.bounds
      bounds.setLocation(frame.x + rootPane.x, frame.y + rootPane.y)
      return bounds
    }
  }

  init {
    if (project.isDefault) {
      return
    }
    val updateHeadersAlarm = SingleAlarm(Runnable { updateToolWindowHeaders() }, 50, project)
    val projectLevelBusConnection = project.messageBus.connect()
    projectLevelBusConnection.subscribe(ToolWindowManagerListener.TOPIC, myDispatcher.multicaster)
    val appLevelBusConnection = ApplicationManager.getApplication().messageBus.connect(
      project)
    appLevelBusConnection.subscribe(AnActionListener.TOPIC, object : AnActionListener {
      override fun beforeActionPerformed(action: AnAction, dataContext: DataContext, event: AnActionEvent) {
        if (myCurrentState != KeyState.hold) {
          resetHoldState()
        }
      }
    })
    appLevelBusConnection.subscribe(ProjectManager.TOPIC, object : ProjectManagerListener {
      var awtFocusListener: AWTEventListener? = null
      override fun projectOpened(project: Project) {
        if (project !== this.project) {
          return
        }
        awtFocusListener = AWTEventListener { event ->
          if (project.isDisposed) {
            return@AWTEventListener
          }
          assert(event is FocusEvent)
          val focusEvent = event as FocusEvent
          if (focusEvent.id == FocusEvent.FOCUS_GAINED) {
            val component = focusEvent.component
            if (component != null) {
              val editorIsGoingToGetFocus = FileEditorManagerEx.getInstanceEx(
                project).splitters.editorComposites.stream()
                .flatMap(
                  { c: EditorWithProviderComposite -> Arrays.stream(c.getEditors()) })
                .anyMatch(
                  { editor: FileEditor ->
                    SwingUtilities.isDescendingFrom(component, editor.getComponent())
                  })
              if (editorIsGoingToGetFocus) {
                myActiveStack.clear()
              }
            }
          }
        }
        Toolkit.getDefaultToolkit().addAWTEventListener(awtFocusListener, AWTEvent.FOCUS_EVENT_MASK)
        init()
        val focusListener = PropertyChangeListener { it: PropertyChangeEvent? -> updateHeadersAlarm.cancelAndRequest() }
        val keyboardFocusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
        keyboardFocusManager.addPropertyChangeListener("focusOwner", focusListener)
        Disposer.register(this.project, Disposable { keyboardFocusManager.removePropertyChangeListener("focusOwner", focusListener) })
      }

      override fun projectClosed(project: Project) {
        if (project === this.project) {
          Toolkit.getDefaultToolkit().removeAWTEventListener(awtFocusListener)
          this@ToolWindowManagerImpl.projectClosed()
        }
      }
    })
    appLevelBusConnection.subscribe(KeymapManagerListener.TOPIC, object : KeymapManagerListener {
      override fun activeKeymapChanged(keymap: Keymap?) {
        idToEntry.forEach(
          { key: String?, info: ToolWindowEntry -> info.stripeButton.updatePresentation() })
      }
    })
    myLayout.copyFrom(WindowManagerEx.getInstanceEx().layout)
    myLayout.infos.forEach(
      java.util.function.Consumer { info: WindowInfoImpl -> myLayout.unregister((Objects.requireNonNull(info.id))!!) })
    projectLevelBusConnection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
      override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
        getFocusManagerImpl(project).doWhenFocusSettlesDown(object : ExpirableRunnable.ForProject(
          project) {
          override fun run() {
            if (!hasOpenEditorFiles()) {
              focusToolWindowByDefault(null)
            }
          }
        })
      }
    })
    val predicate = Predicate { event: AWTEvent ->
      (event.getID() == FocusEvent.FOCUS_LOST
      ) || (event.getID() == FocusEvent.FOCUS_GAINED
           ) || (event.getID() == MouseEvent.MOUSE_PRESSED
                ) || (event.getID() == KeyEvent.KEY_PRESSED)
    }
    Windows.ToolWindowFilter
      .filterBySignal(Windows.Signal(predicate))
      .withEscAction()
      .handleDocked({ toolWindowId: String? -> })
      .handleFloating({ toolWindowId: String? -> })
      .handleFocusLostOnPinned({ toolWindowId: String ->
                                 val commands: MutableList<FinalizableCommand> = ArrayList()
                                 if (isToolWindowRegistered(toolWindowId)) {
                                   deactivateToolWindowImpl(
                                     getRegisteredInfoOrLogError(toolWindowId), true, commands)
                                   execute(commands,
                                           true) // notify clients that toolwindow is deactivated
                                 }
                               })
      .handleWindowed({ toolWindowId: String? -> })
      .bind(project)
    checkInvariants("")
  }
}