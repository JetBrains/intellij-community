// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.frame

import com.intellij.ide.IdeBundle
import com.intellij.ide.OccurenceNavigator
import com.intellij.ide.ui.text.StyledTextPane
import com.intellij.ide.ui.text.paragraph.TextParagraph
import com.intellij.ide.ui.text.parts.RegularTextPart
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.NonProportionalOnePixelSplitter
import com.intellij.openapi.util.Disposer
import com.intellij.ui.ListSpeedSearch
import com.intellij.ui.PopupHandler
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SpeedSearchComparator
import com.intellij.util.concurrency.EdtExecutorService
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.NamedColorUtil
import com.intellij.util.ui.TextTransferable
import com.intellij.util.ui.UIUtil
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebugSessionListener
import com.intellij.xdebugger.frame.XExecutionStack
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.frame.XSuspendContext
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.actions.XDebuggerActions
import com.intellij.xdebugger.impl.frame.XFramesView.addFramesNavigationAd
import com.intellij.xdebugger.impl.ui.XDebugSessionTab3
import com.intellij.xdebugger.impl.util.SequentialDisposables
import com.intellij.xdebugger.impl.util.isNotAlive
import com.intellij.xdebugger.impl.util.onTermination
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.Rectangle
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.time.Duration
import java.util.*
import java.util.concurrent.CompletableFuture
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener
import javax.swing.text.StyleConstants
import kotlin.time.toKotlinDuration

@Internal
class XThreadsFramesView(val debugTab: XDebugSessionTab3) : XDebugView() {
  private val myPauseDisposables = SequentialDisposables(this)

  private val supportsDescription: Boolean = debugTab.project.service<XDebuggerExecutionStackDescriptionService>().isAvailable()

  private val myThreadsList = XDebuggerThreadsList.createDefault(supportsDescription)
  private val myFramesList = XDebuggerFramesList(debugTab.project)

  private val myCurrentThreadDescriptionComponent = StyledTextPane().apply {
    Disposer.register(this@XThreadsFramesView, this)
  }

  private val mySplitter: NonProportionalOnePixelSplitter

  private var myListenersEnabled = false

  private var myFramesManager: FramesManager
  private var myThreadsContainer: ThreadsContainer

  val threads: XDebuggerThreadsList get() = myThreadsList
  val frames: XDebuggerFramesList get() = myFramesList

  private val myFramesPresentationCache = mutableMapOf<Any, String>()

  private val mainPanel = JPanel(BorderLayout())

  private var stackInfoDescriptionRequester: StackInfoDescriptionRequester?

  override fun getMainComponent(): JComponent {
    return mainPanel
  }
  val defaultFocusedComponent: JComponent = myFramesList

  companion object {
    private const val SPLITTER_PROPORTION_KEY: String = "XThreadsFramesViewSplitterKey"
    private const val SPLITTER_PROPORTION_DEFAULT_VALUE: Float = 0.5f

    private fun Component.toScrollPane(): JScrollPane {
      return ScrollPaneFactory.createScrollPane(this)
    }

    private fun <T> T.withSpeedSearch(
      shouldMatchFromTheBeginning: Boolean = false,
      shouldMatchCamelCase: Boolean = true,
      converter: ((Any?) -> String?)? = null,
    ): T where T : JList<*> {
      val search = if (converter != null) ListSpeedSearch.installOn(this, converter) else ListSpeedSearch.installOn(this)
      search.comparator = SpeedSearchComparator(shouldMatchFromTheBeginning, shouldMatchCamelCase)
      return this
    }
  }

  private fun XDebuggerFramesList.withSpeedSearch(
    shouldMatchFromTheBeginning: Boolean = false,
    shouldMatchCamelCase: Boolean = true,
  ): XDebuggerFramesList {

    val coloredStringBuilder = TextTransferable.ColoredStringBuilder()
    fun getPresentation(element: Any?): String? {
      element ?: return null

      return myFramesPresentationCache.getOrPut(element) {
        when (element) {
          is XStackFrame -> {
            element.customizePresentation(coloredStringBuilder)
            val value = coloredStringBuilder.builder.toString()
            coloredStringBuilder.builder.clear()
            value
          }

          else -> toString()
        }
      }
    }

    return withSpeedSearch(shouldMatchFromTheBeginning, shouldMatchCamelCase, ::getPresentation)
  }

  fun setThreadsVisible(visible: Boolean) {
    if (mySplitter.firstComponent.isVisible == visible) return

    mySplitter.firstComponent.isVisible = visible
    mySplitter.revalidate()
    mySplitter.repaint()
  }

  fun isThreadsViewVisible(): Boolean = mySplitter.firstComponent.isVisible

  init {
    val disposable = myPauseDisposables.next()
    myFramesManager = FramesManager(myFramesList, disposable)
    myThreadsContainer = ThreadsContainer(myThreadsList, null, disposable)
    myPauseDisposables.terminateCurrent()

    val splitter = object : NonProportionalOnePixelSplitter(
      false,
      SPLITTER_PROPORTION_KEY,
      SPLITTER_PROPORTION_DEFAULT_VALUE,
      debugTab, debugTab.project), OccurenceNavigator by myFramesList {}

    val minimumDimension = Dimension(JBUI.scale(26), 0)
    val threadsScrollPane = myThreadsList.withSpeedSearch().toScrollPane()
    splitter.firstComponent = threadsScrollPane.apply {
      minimumSize = minimumDimension
    }

    val frameListWrapper = JPanel(BorderLayout(0, 0))

    if (supportsDescription) {
      frameListWrapper.add(
        JPanel(BorderLayout()).apply {
          border = JBEmptyBorder(0, 20, 0, 0)
          add(myCurrentThreadDescriptionComponent)
        },
        BorderLayout.NORTH)
    }
    addFramesNavigationAd(frameListWrapper)
    frameListWrapper.add(myFramesList.withSpeedSearch().toScrollPane(), BorderLayout.CENTER)
    frameListWrapper.minimumSize = minimumDimension

    splitter.secondComponent = frameListWrapper

    stackInfoDescriptionRequester = debugTab.session?.let { session ->
      if (supportsDescription) {
        val requester = StackInfoDescriptionRequester(myThreadsList, session, mainPanel)
        threadsScrollPane.viewport.addChangeListener(requester)
        myThreadsList.model.addListDataListener(requester)
        session.addSessionListener(object : XDebugSessionListener {
          override fun sessionStopped() {
            myThreadsList.model.removeListDataListener(requester)
            threadsScrollPane.viewport.removeChangeListener(requester)
            stackInfoDescriptionRequester = null
            myCurrentThreadDescriptionComponent.clear()
          }
        })
        requester
      }
      else {
        null
      }
    }

    mySplitter = splitter

    mainPanel.add(mySplitter, BorderLayout.CENTER)

    myThreadsList.addListSelectionListener { e ->
      if (e.valueIsAdjusting || !myListenersEnabled) return@addListSelectionListener
      val stack = myThreadsList.selectedValue?.stack ?: return@addListSelectionListener

      val session = getSession(e) ?: return@addListSelectionListener
      stack.setActive(session)
    }

    myThreadsList.addMouseListener(object : PopupHandler() {
      override fun invokePopup(comp: Component, x: Int, y: Int) {
        val actionManager = ActionManager.getInstance()
        val group = actionManager.getAction(XDebuggerActions.THREADS_TREE_POPUP_GROUP) as? ActionGroup ?: return
        actionManager.createActionPopupMenu("XDebuggerThreadsList", group).component.show(comp, x, y)
      }
    })

    myThreadsList.addMouseListener(object : MouseAdapter() {
      // not mousePressed here, otherwise click in unfocused frames list transfers focus to the new opened editor
      override fun mouseReleased(e: MouseEvent) {
        if (!myListenersEnabled) return

        val i = myThreadsList.locationToIndex(e.point)
        if (i == -1 || !myThreadsList.isSelectedIndex(i)) return

        val session = getSession(e) ?: return
        val stack = myThreadsList.selectedValue?.stack ?: return

        stack.setActive(session)
      }
    })

    myFramesList.addListSelectionListener {
      if (it.valueIsAdjusting || !myListenersEnabled) return@addListSelectionListener

      val session = getSession(it) ?: return@addListSelectionListener
      val stack = myThreadsList.selectedValue?.stack ?: return@addListSelectionListener
      val frame = myFramesList.selectedValue as? XStackFrame ?: return@addListSelectionListener

      session.setCurrentStackFrame(stack, frame)
    }

    myFramesList.addMouseListener(object : PopupHandler() {
      override fun invokePopup(comp: Component, x: Int, y: Int) {
        val actionManager = ActionManager.getInstance()
        val group = actionManager.getAction(XDebuggerActions.FRAMES_TREE_POPUP_GROUP) as ActionGroup
        actionManager.createActionPopupMenu("XDebuggerFramesList", group).component.show(comp, x, y)
      }
    })

    myFramesList.addMouseListener(object : MouseAdapter() {
      // not mousePressed here, otherwise click in unfocused frames list transfers focus to the new opened editor
      override fun mouseReleased(e: MouseEvent) {
        if (!myListenersEnabled) return

        val i = myFramesList.locationToIndex(e.point)
        if (i == -1 || !myFramesList.isSelectedIndex(i)) return

        val session = getSession(e) ?: return
        val stack = myThreadsList.selectedValue?.stack ?: return
        val frame = myFramesList.selectedValue as? XStackFrame ?: return

        session.setCurrentStackFrame(stack, frame)
      }
    })
  }

  private fun setActiveThreadDescription(@Nls text: String) {
    myCurrentThreadDescriptionComponent.paragraphs = listOf(TextParagraph(listOf(RegularTextPart(text).apply {
      this.editAttributes {
        StyleConstants.setForeground(this, NamedColorUtil.getInactiveTextColor())
      }
    })))
    myCurrentThreadDescriptionComponent.revalidate()
    myCurrentThreadDescriptionComponent.repaint()
  }

  override fun processSessionEvent(event: SessionEvent, session: XDebugSession) {
    if (event == SessionEvent.BEFORE_RESUME) {
      return
    }
    val suspendContext = session.suspendContext
    if (suspendContext == null) {
      requestClear()
      return
    }

    UIUtil.invokeLaterIfNeeded {
      if (event == SessionEvent.PAUSED || event == SessionEvent.SETTINGS_CHANGED && session.isSuspended) {
        // clear immediately
        cancelClear()
        clear()

        start(session)
        return@invokeLaterIfNeeded
      }

      if (event == SessionEvent.FRAME_CHANGED) {
        val currentExecutionStack = (session as XDebugSessionImpl).currentExecutionStack
        val currentStackFrame = session.currentStackFrame

        var selectedStack = threads.selectedValue?.stack
        if (selectedStack != currentExecutionStack) {
          val newSelectedItemIndex = threads.model.items.indexOfFirst { it.stack == currentExecutionStack }
          if (newSelectedItemIndex != -1) {
            threads.selectedIndex = newSelectedItemIndex
            myFramesManager.refresh()
            selectedStack = threads.selectedValue?.stack
          }
        }
        if (selectedStack != currentExecutionStack)
          return@invokeLaterIfNeeded

        val selectedFrame = frames.selectedValue
        if (selectedFrame != currentStackFrame) {
          frames.setSelectedValue(currentStackFrame, true)
        }
      }

      if (event == SessionEvent.SETTINGS_CHANGED) {
        myFramesManager.refresh()
      }
    }
  }

  fun start(session: XDebugSession) {
    val suspendContext = session.suspendContext ?: return
    val disposable = nextDisposable()

    myFramesManager = FramesManager(myFramesList, disposable)
    val activeStack = suspendContext.activeExecutionStack
    activeStack?.setActive(session)

    myThreadsContainer = ThreadsContainer(myThreadsList, activeStack, disposable)
    myThreadsContainer.start(suspendContext)
  }

  private fun XExecutionStack.setActive(session: XDebugSession) {
    myFramesManager.setActive(this)

    setActiveThreadDescription(IdeBundle.message("progress.text.loading"))
    stackInfoDescriptionRequester?.requestDescription(this) { description, exception ->
      description?.let { setActiveThreadDescription(it.longDescription) }
    }

    val currentFrame = myFramesManager.tryGetCurrentFrame(this) ?: return

    session.setCurrentStackFrame(this, currentFrame)
  }

  private fun nextDisposable(): Disposable {
    val disposable = myPauseDisposables.next()
    disposable.onTermination {
      myListenersEnabled = false
    }

    myListenersEnabled = true
    return disposable
  }

  override fun clear() {
    myPauseDisposables.terminateCurrent()
    myThreadsList.clear()
    myFramesList.clear()
    myThreadsContainer.clear()
    myFramesPresentationCache.clear()
    stackInfoDescriptionRequester?.clear()
    myCurrentThreadDescriptionComponent.clear()
  }

  override fun dispose() {}

  private class FramesContainer(
    private val myDisposable: Disposable,
    private val myFramesList: XDebuggerFramesList,
    private val myExecutionStack: XExecutionStack,
  ) : XStackFrameContainerEx {
    private var isActive = false

    private var isProcessed = false
    private var isStarted = false

    private var mySelectedValue: Any? = null
    private var myVisibleRectangle: Rectangle? = null
    private val myItems = mutableListOf<Any?>()

    val currentFrame: XStackFrame?
      get() = myFramesList.selectedValue as? XStackFrame

    fun startIfNeeded() {
      UIUtil.invokeLaterIfNeeded {
        if (isStarted)
          return@invokeLaterIfNeeded

        isStarted = true
        myItems.add(null) // loading
        myExecutionStack.computeStackFrames(0, this)
      }
    }

    fun setActive(activeDisposable: Disposable) {
      startIfNeeded()

      isActive = true
      activeDisposable.onTermination {
        isActive = false
        myVisibleRectangle = myFramesList.visibleRect
        mySelectedValue = if (myFramesList.isSelectionEmpty) null else myFramesList.selectedValue
      }

      updateView()
    }

    private fun updateView() {
      if (!isActive) return

      myFramesList.model.replaceAll(myItems)
      if (mySelectedValue != null) {
        myFramesList.setSelectedValue(mySelectedValue, true)
      }
      else if (myFramesList.model.items.isNotEmpty()) {
        myFramesList.selectedIndex = 0
        mySelectedValue = myFramesList.selectedValue
      }

      val visibleRectangle = myVisibleRectangle
      if (visibleRectangle != null)
        myFramesList.scrollRectToVisible(visibleRectangle)
      myFramesList.repaint()
    }

    override fun errorOccurred(errorMessage: String) {
      addStackFramesInternal(mutableListOf(errorMessage), null, true)
    }

    override fun addStackFrames(stackFrames: List<XStackFrame>, toSelect: XStackFrame?, last: Boolean) {
      addStackFramesInternal(stackFrames, toSelect, last)
    }

    override fun addStackFrames(stackFrames: List<XStackFrame>, last: Boolean) {
      addStackFrames(stackFrames, null, last)
    }

    private fun addStackFramesInternal(stackFrames: List<*>, toSelect: XStackFrame?, last: Boolean) {
      invokeIfNeeded {
        val insertIndex = myItems.size - 1

        if (stackFrames.isNotEmpty()) {
          myItems.addAll(insertIndex, stackFrames)
        }

        if (last) {
          // remove loading
          myItems.removeAt(myItems.size - 1)
          isProcessed = true
        }

        if (toSelect != null && myItems.contains(toSelect)) {
          mySelectedValue = toSelect
        }

        updateView()
      }
    }

    private fun invokeIfNeeded(action: () -> Unit) {
      UIUtil.invokeLaterIfNeeded {
        if (isProcessed || myDisposable.isNotAlive)
          return@invokeLaterIfNeeded

        action()
      }
    }
  }

  private class FramesManager(
    private val myFramesList: XDebuggerFramesList,
    private val disposable: Disposable,
  ) {

    private val myMap = mutableMapOf<StackInfo, FramesContainer>()
    private val myActiveStackDisposables = SequentialDisposables(disposable)
    private var myActiveStack: XExecutionStack? = null

    init {
      Disposer.register(disposable, Disposable {
        myActiveStack = null
      })
    }

    private fun XExecutionStack.getContainer(): FramesContainer {
      return myMap.getOrPut(StackInfo.from(this)) {
        FramesContainer(disposable, myFramesList, this)
      }
    }

    fun setActive(stack: XExecutionStack) {
      val disposable = myActiveStackDisposables.next()
      myActiveStack = stack
      stack.getContainer().setActive(disposable)
    }

    fun tryGetCurrentFrame(stack: XExecutionStack): XStackFrame? {
      return stack.getContainer().currentFrame
    }

    fun refresh() {
      myMap.clear()
      setActive(myActiveStack ?: return)
    }
  }

  private class ThreadsContainer(
    private val myThreadsList: XDebuggerThreadsList,
    private var myInitialActiveThread: XExecutionStack?,
    private val myDisposable: Disposable,
  ) : XSuspendContext.XExecutionStackContainer {
    private var isProcessed = false
    private var isStarted = false

    companion object {
      private val loading = listOf(StackInfo.LOADING)
    }

    fun start(suspendContext: XSuspendContext) {
      UIUtil.invokeLaterIfNeeded {
        if (isStarted) return@invokeLaterIfNeeded

        val activeStack = myInitialActiveThread
        if (activeStack != null) {
          myThreadsList.model.replaceAll(listOf(StackInfo.from(activeStack), StackInfo.LOADING))
        }
        else {
          myThreadsList.model.replaceAll(loading)
        }

        myThreadsList.selectedIndex = 0
        suspendContext.computeExecutionStacks(this)
        isStarted = true
      }
    }

    override fun errorOccurred(errorMessage: String) {
      invokeIfNeeded {
        val model = myThreadsList.model
        // remove loading
        model.remove(model.size - 1)
        model.add(listOf(StackInfo.error(errorMessage)))
        isProcessed = true
      }
    }

    override fun addExecutionStack(executionStacks: List<XExecutionStack>, last: Boolean) {
      invokeIfNeeded {
        val model = myThreadsList.model
        val insertIndex = model.size - 1

        val threads = getThreadsList(executionStacks)
        if (threads.any()) {
          model.addAll(insertIndex, threads)
        }

        if (last) {
          // remove loading
          model.remove(model.size - 1)
          isProcessed = true
        }

        myThreadsList.repaint()
      }
    }

    fun clear() {
      myInitialActiveThread = null
    }

    private fun getThreadsList(executionStacks: List<XExecutionStack>): List<StackInfo> {
      var sequence = executionStacks.asSequence()
      if (myInitialActiveThread != null) {
        sequence = sequence.filter { it != myInitialActiveThread }
      }
      return sequence.map { StackInfo.from(it) }.toList()
    }

    private fun invokeIfNeeded(action: () -> Unit) {
      UIUtil.invokeLaterIfNeeded {
        if (isProcessed || myDisposable.isNotAlive) {
          return@invokeLaterIfNeeded
        }

        action()
      }
    }
  }
}

@OptIn(FlowPreview::class)
internal class StackInfoDescriptionRequester(
  private val threadsList: XDebuggerThreadsList,
  val session: XDebugSessionImpl,
  val viewComponent: JComponent,
) : javax.swing.event.ChangeListener, ListDataListener {

  companion object {
    private val logger = Logger.getInstance(StackInfoDescriptionRequester::class.java)
    private val DESCRIPTION_GROUPING_EVENT_TIMEOUT: kotlin.time.Duration = Duration.ofMillis(500).toKotlinDuration()
  }

  private val descriptionCalculationMap = Collections.synchronizedMap(mutableMapOf<XExecutionStack, CompletableFuture<XDebuggerExecutionStackDescription>>())
  private val descriptionRequestsFlow = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  init {
    session.coroutineScope.launch(Dispatchers.EDT) {
      descriptionRequestsFlow.debounce(DESCRIPTION_GROUPING_EVENT_TIMEOUT).collectLatest {
        val firstIndex = threadsList.firstVisibleIndex
        var lastIndex = threadsList.lastVisibleIndex

        if (firstIndex < 0) return@collectLatest
        lastIndex = if (lastIndex < 0) threadsList.model.size - 1 else lastIndex
        for (stackInfo in threadsList.model.items.subList(firstIndex, lastIndex + 1)) {
          requestDescription(stackInfo)
        }
      }
    }
  }

  override fun stateChanged(e: javax.swing.event.ChangeEvent) {
    triggerDescriptionCalculationForVisiblePart()
    return
  }

  internal fun clear() {
    descriptionCalculationMap.forEach { (_, future) -> future.cancel(true) }
    descriptionCalculationMap.clear()
  }

  internal fun triggerDescriptionCalculationForVisiblePart() {
    descriptionRequestsFlow.tryEmit(Unit)
  }

  internal fun requestDescription(executionStack: XExecutionStack, onFinished: (XDebuggerExecutionStackDescription?, Throwable?) -> Unit) {
    val descriptionService = session.project.service<XDebuggerExecutionStackDescriptionService>()
    if (!(descriptionService.isAvailable())) return

    descriptionCalculationMap.getOrPut(executionStack) {
      descriptionService.getExecutionStackDescription(executionStack, session).asCompletableFuture()
    }.whenCompleteAsync({ result: XDebuggerExecutionStackDescription?, exception: Throwable? ->
      onFinished(result, exception)
      viewComponent.repaint()
    }, EdtExecutorService.getInstance())
  }

  internal fun requestDescription(stackInfo: StackInfo) {
    val executionStack = stackInfo.stack ?: return

    requestDescription(executionStack) { result, exception ->
      if (exception is CancellationException) {
        return@requestDescription
      }
      if (exception != null) {
        logger.error(exception)
      }
      if (result != null) {
        stackInfo.description = result.shortDescription
      }
    }
  }

  override fun intervalAdded(e: ListDataEvent?) {
    triggerDescriptionCalculationForVisiblePart()
  }

  override fun intervalRemoved(e: ListDataEvent?) {
    triggerDescriptionCalculationForVisiblePart()
  }

  override fun contentsChanged(e: ListDataEvent?) {
    triggerDescriptionCalculationForVisiblePart()
  }
}
