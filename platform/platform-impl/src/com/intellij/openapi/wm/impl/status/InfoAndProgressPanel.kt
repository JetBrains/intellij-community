// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")
@file:OptIn(FlowPreview::class)

package com.intellij.openapi.wm.impl.status

import com.intellij.featureStatistics.FeatureUsageTracker
import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.PowerSaveMode
import com.intellij.ide.impl.ProjectUtil.getActiveProject
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettings.Companion.getInstance
import com.intellij.ide.ui.UISettingsListener
import com.intellij.internal.statistic.service.fus.collectors.UIEventLogger.ProgressPaused
import com.intellij.internal.statistic.service.fus.collectors.UIEventLogger.ProgressResumed
import com.intellij.notification.impl.ApplicationNotificationsModel
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.fileEditor.impl.MergingUpdateChannel
import com.intellij.openapi.observable.util.addMouseHoverListener
import com.intellij.openapi.progress.ProgressModel
import com.intellij.openapi.progress.TaskInfo
import com.intellij.openapi.progress.impl.ProgressSuspender
import com.intellij.openapi.progress.impl.ProgressSuspender.SuspenderListener
import com.intellij.openapi.progress.util.TitledIndicator
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.getParentOfType
import com.intellij.openapi.ui.panel.ProgressPanel
import com.intellij.openapi.ui.panel.ProgressPanelBuilder
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.BalloonHandler
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.impl.status.IdeStatusBarImpl.Companion.WIDGET_EFFECT_KEY
import com.intellij.platform.util.coroutines.flow.throttle
import com.intellij.reference.SoftReference
import com.intellij.ui.*
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.hover.HoverListener
import com.intellij.ui.util.width
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.containers.JBIterable
import com.intellij.util.containers.UnmodifiableHashMap
import com.intellij.util.ui.*
import com.intellij.util.ui.StartupUiUtil.getCenterPoint
import it.unimi.dsi.fastutil.ints.IntArrays
import it.unimi.dsi.fastutil.ints.IntComparator
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import org.jetbrains.annotations.ApiStatus
import java.awt.*
import java.awt.event.ActionListener
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.lang.ref.WeakReference
import javax.swing.*
import javax.swing.event.HyperlinkListener
import kotlin.math.max
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@ApiStatus.Internal
class InfoAndProgressPanel internal constructor(
  private val statusBar: IdeStatusBarImpl,
  private val coroutineScope: CoroutineScope,
) : UISettingsListener {
  companion object {
    @JvmField
    @ApiStatus.Internal
    val FAKE_BALLOON: Any = Any()

    private const val SHOW_COUNTER_REGISTRY_KEY: String = "progresses.show.counter.icon.instead.of.show.link"

    private val showCounterInsteadOfMultiProcessLink: Boolean
      get() = Registry.`is`(SHOW_COUNTER_REGISTRY_KEY, false)

    private val supportSecondaryProgresses: Boolean
      get() = showCounterInsteadOfMultiProcessLink && Registry.`is`("progresses.support.secondary.progresses", false)
  }

  @ApiStatus.Internal
  enum class AutoscrollLimit {
    NOT_ALLOWED,
    ALLOW_ONCE,
    UNLIMITED
  }

  @ApiStatus.Internal
  interface ScrollableToSelected {
    fun updateAutoscrollLimit(limit: AutoscrollLimit)
  }

  private var popup: ProcessPopup? = null
  private val balloon = ProcessBalloon(3)

  private val mainPanel = InfoAndProgressPanelImpl(this)

  @get:JvmName("getComponent")
  internal val component: JPanel
    get() = mainPanel

  private val originals = ArrayList<ProgressModel>()
  private val infos = ArrayList<TaskInfo>()
  private var inlineToOriginal = UnmodifiableHashMap.empty<MyProgressComponent, ProgressModel>()
  private val originalToInlines = HashMap<ProgressModel, MutableSet<MyProgressComponent>>()
  private var shouldClosePopupAndOnProcessFinish = false
  private var currentRequestor: String? = null
  private var disposed = false
  private var lastShownBalloon: WeakReference<Balloon>? = null
  private val dirtyIndicators = ReferenceOpenHashSet<ProgressComponent>()

  private val runQueryRequests = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_LATEST)
  private val updateRequests = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  private val removeProgressRequests = MergingUpdateChannel<MyProgressComponent>(delay = 50.milliseconds) { toUpdate ->
    withContext(Dispatchers.EDT) {
      for (indicator in toUpdate) {
        removeProgress(indicator)
      }
    }
  }
  private val lifecycleDisposable = Disposer.newDisposable()

  init {
    val connection = ApplicationManager.getApplication().getMessageBus().connect(coroutineScope = coroutineScope)
    connection.subscribe(PowerSaveMode.TOPIC, PowerSaveMode.Listener {
      EdtInvocationManager.invokeLaterIfNeeded(::updateProgressIcon)
      queueProgressUpdateForIndicators()
    })
    connection.subscribe(ProgressSuspender.TOPIC, object : SuspenderListener {
      override fun suspendableProgressAppeared(suspender: ProgressSuspender) {
        EdtInvocationManager.invokeLaterIfNeeded(::updateProgressIcon)
        queueProgressUpdateForIndicators()
      }

      override fun suspendedStatusChanged(suspender: ProgressSuspender) {
        EdtInvocationManager.invokeLaterIfNeeded(::updateProgressIcon)
        queueProgressUpdateForIndicators()
      }
    })

    coroutineScope.launch {
      runQueryRequests
        .debounce(2.seconds)
        .collectLatest {
          runQuery()
        }
    }
    coroutineScope.launch(ModalityState.any().asContextElement()) {
      updateRequests
        .throttle(50)
        .collect {
          var indicators: List<ProgressComponent>
          synchronized(dirtyIndicators) {
            indicators = ArrayList(dirtyIndicators)
            dirtyIndicators.clear()
          }
          withContext(Dispatchers.EDT) {
            for (indicator in indicators) {
              indicator.updateAndRepaint()
            }
          }
        }
    }
    coroutineScope.launch(ModalityState.any().asContextElement()) {
      removeProgressRequests.start()
    }

    coroutineScope.coroutineContext.job.invokeOnCompletion {
      // it is important to dispose indicators (Disposer.dispose(indicator))
      dispose()
    }
  }

  private fun queueProgressUpdateForIndicators() {
    for (indicator in inlineToOriginal.keys) {
      if (indicator.canCheckPowerSaveMode()) {
        indicator.queueProgressUpdate()
      }
    }
  }

  private val rootPane: JRootPane?
    get() = mainPanel.rootPane

  internal fun handle(e: MouseEvent) {
    if (UIUtil.isActionClick(e, MouseEvent.MOUSE_PRESSED)) {
      triggerPopupShowing()
    }
  }

  @ApiStatus.Experimental
  fun setCentralComponent(component: JComponent?) {
    mainPanel.setCentralComponent(component)
  }

  private fun dispose() {
    synchronized(originals) {
      for (indicator in inlineToOriginal.keys) {
        Disposer.dispose(indicator)
      }
      inlineToOriginal = UnmodifiableHashMap.empty()
      originalToInlines.clear()
      Disposer.dispose(lifecycleDisposable)
      disposed = true
      infos.clear()
    }
  }

  val backgroundProcesses: List<Pair<TaskInfo, ProgressModel>>
    get() {
      synchronized(originals) {
        if (disposed || originals.isEmpty()) {
          return emptyList()
        }

        val result = ArrayList<Pair<TaskInfo, ProgressModel>>(originals.size)
        for (i in originals.indices) {
          result.add(Pair(infos[i], originals[i]))
        }
        return result
      }
    }

  private fun getPopup(): ProcessPopup {
    var result = popup
    if (result == null) {
      result = ProcessPopup(this)
      popup = result
    }
    return result
  }

  @RequiresEdt
  fun addProgress(original: ProgressModel, info: TaskInfo) {
    // `openProcessPopup` may require the dispatch thread
    ThreadingAssertions.assertEventDispatchThread()
    synchronized(originals) {
      if (originals.isEmpty()) {
        mainPanel.updateNavBarAutoscrollToSelectedLimit(AutoscrollLimit.ALLOW_ONCE)
        getPopup().setHideOnFocusLost(false)
      }
      originals.add(original)
      infos.add(info)
      val expanded = createInlineDelegate(info = info, original = original, compact = false)
      val compact = createInlineDelegate(info = info, original = original, compact = true)
      getPopup().addIndicator(expanded)
      balloon.addIndicator(rootPane, compact)
      updateProgressIcon()
      mainPanel.updateProgress(compact)
      // we don't want the popup to activate another project's window or be shown above current project's window
      if (infos.size > 1 && Registry.`is`("ide.windowSystem.autoShowProcessPopup", false) && statusBar.project === getActiveProject()) {
        openProcessPopup(false)
      }
      if (original.isFinished(info)) {
        // already finished, progress might not send another finished message
        removeProgress(expanded)
        removeProgress(compact)
        return
      }
      coroutineScope.launch {
        runQuery()
      }
    }
  }

  private fun hasProgressIndicators(): Boolean = synchronized(originals) { !originals.isEmpty() }

  @RequiresEdt
  private fun removeProgress(progress: MyProgressComponent) {
    ThreadingAssertions.assertEventDispatchThread()
    synchronized(originals) {
      // already disposed
      if (!inlineToOriginal.containsKey(progress)) {
        return
      }
      val last = originals.size == 1
      if (!progress.isCompact && popup != null) {
        popup!!.removeIndicator(progress)
      }
      val original = removeFromMaps(progress)
      if (originals.contains(original)) {
        Disposer.dispose(progress)
        if (progress.isCompact) {
          balloon.removeIndicator(rootPane, progress)
        }
        return
      }
      mainPanel.removeProgress(progress, last)
      coroutineScope.launch {
        runQuery()
      }
    }
    Disposer.dispose(progress)
    if (progress.isCompact) {
      balloon.removeIndicator(rootPane, progress)
      statusBar.notifyProgressRemoved(backgroundProcesses)
    }
  }

  private fun removeFromMaps(progress: MyProgressComponent): ProgressModel? {
    val original = inlineToOriginal.get(progress)
    inlineToOriginal = inlineToOriginal.without(progress)
    synchronized(dirtyIndicators) { dirtyIndicators.remove(progress) }
    var set = originalToInlines.get(original)
    if (set != null) {
      set.remove(progress)
      if (set.isEmpty()) {
        set = null
        originalToInlines.remove(original)
      }
    }
    if (set == null) {
      val originalIndex = originals.indexOf(original)
      originals.removeAt(originalIndex)
      infos.removeAt(originalIndex)
    }
    return original
  }

  internal fun setInlineProgressByWeight() {
    synchronized(originals) {
      val size = infos.size
      val indexes = IntArray(size) { it }
      IntArrays.stableSort(indexes, 0, size, IntComparator { index1, index2 ->
        val shown1 = originals.get(index1).visibleInStatusBar
        val shown2 = originals.get(index2).visibleInStatusBar
        if (shown1 != shown2) {
          shown2.compareTo(shown1)
        }
        else {
          infos.get(index1).statusBarIndicatorWeight - infos.get(index2).statusBarIndicatorWeight
        }
      })
      var index = -1
      for (i in 0 until size) {
        val suspender = ProgressSuspender.getSuspender(originals[indexes[i]].getProgressIndicator())
        if (suspender == null || !suspender.isSuspended) {
          index = i
          break
        }
      }
      val resultIndex = indexes[if (index == -1) 0 else index]
      mainPanel.updateProgressState(createInlineDelegate(infos[resultIndex], originals[resultIndex], true))
    }
  }

  private fun openProcessPopup(requestFocus: Boolean) {
    var shouldClosePopupAndOnProcessFinish: Boolean
    synchronized(originals) {
      if (popup != null && popup!!.isShowing) {
        return
      }

      getPopup().show(requestFocus)
      shouldClosePopupAndOnProcessFinish = hasProgressIndicators()
      mainPanel.updateProgressState(true)
    }
    this.shouldClosePopupAndOnProcessFinish = shouldClosePopupAndOnProcessFinish
  }

  fun hideProcessPopup() {
    synchronized(originals) {
      if (popup == null || !popup!!.isShowing) {
        return
      }
      popup!!.hide()
      mainPanel.updateProgressState(false)
    }
  }

  fun setText(text: @NlsContexts.StatusBarText String?, requestor: String?): @NlsContexts.StatusBarText String? {
    if (mainPanel.showNavBar) {
      return text
    }

    if (text.isNullOrEmpty() && requestor != currentRequestor && ApplicationNotificationsModel.EVENT_REQUESTOR != requestor) {
      return mainPanel.statusPanel.text
    }
    val logMode = mainPanel.statusPanel.updateText(if (ApplicationNotificationsModel.EVENT_REQUESTOR == requestor) "" else text)
    currentRequestor = if (logMode) ApplicationNotificationsModel.EVENT_REQUESTOR else requestor
    return text
  }

  fun setRefreshVisible(tooltip: @NlsContexts.Tooltip String?) {
    UIUtil.invokeLaterIfNeeded(Runnable {
      if (!mainPanel.showNavBar) {
        mainPanel.refreshIcon.isVisible = true
        mainPanel.refreshIcon.setToolTipText(tooltip)
      }
      else {
        VfsRefreshIndicatorWidgetFactory.start(statusBar, tooltip!!)
      }
    })
  }

  internal fun setRefreshHidden() {
    mainPanel.setRefreshHidden()
  }

  fun notifyByBalloon(
    type: MessageType,
    htmlBody: @NlsContexts.PopupContent String,
    icon: Icon?,
    listener: HyperlinkListener?,
  ): BalloonHandler {
    @Suppress("HardCodedStringLiteral")
    val htmlContent = htmlBody.replace("\n", "<br>")
    val icon = icon ?: if (type == MessageType.INFO) {
      null
    }
    else {
      type.defaultIcon
    }
    val balloon = JBPopupFactory.getInstance()
      .createHtmlTextBalloonBuilder(htmlContent,
                                    icon,
                                    type.titleForeground,
                                    type.popupBackground,
                                    listener)
      .setPointerSize(JBUI.size(16, 8))
      .setPointerShiftedToStart(true)
      .setBorderInsets(JBUI.insets(9, 7, 11, 7))
      .setBorderColor(type.borderColor)
      .setCornerRadius(JBUI.scale(8))
      .createBalloon()
    SwingUtilities.invokeLater(Runnable {
      val oldBalloon = SoftReference.dereference(lastShownBalloon)
      if (oldBalloon != null) {
        balloon.setAnimationEnabled(false)
        oldBalloon.setAnimationEnabled(false)
        oldBalloon.hide()
      }
      lastShownBalloon = WeakReference(balloon)
      val comp: Component = mainPanel
      val targetComponent = mainPanel.inlinePanel.indicator?.textPanel ?: comp
      if (comp.isShowing()) {
        // in the middle of the text label, shifted slightly left
        val x = targetComponent.width / 2 - 5
        // above the label, 9 = pointer height + 1
        val y = -9
        val point = Point(x, y)
        balloon.show(RelativePoint(targetComponent, point), Balloon.Position.above)
      }
      else {
        val rootPane = SwingUtilities.getRootPane(comp)
        if (rootPane != null && rootPane.isShowing()) {
          val contentPane = rootPane.contentPane
          val bounds = contentPane.bounds
          val target = getCenterPoint(bounds, JBUI.size(1, 1))
          target.y = bounds.height - 3
          balloon.show(RelativePoint(contentPane, target), Balloon.Position.above)
        }
      }
    })
    return BalloonHandler { SwingUtilities.invokeLater(Runnable { balloon.hide() }) }
  }

  private fun createInlineDelegate(info: TaskInfo, original: ProgressModel, compact: Boolean): MyProgressComponent {
    val inlines = originalToInlines.computeIfAbsent(original) { HashSet() }
    if (!inlines.isEmpty()) {
      for (eachInline in inlines) {
        if (eachInline.isCompact == compact) {
          return eachInline
        }
      }
    }
    val inline = if (compact) MyProgressComponent(info, original) else ProgressPanelProgressComponent(info, original)
    inlineToOriginal = inlineToOriginal.with(inline, original)
    inlines.add(inline)
    if (compact) {
      inline.component.addMouseListener(object : MouseAdapter() {
        override fun mousePressed(e: MouseEvent) {
          handle(e)
        }

        override fun mouseReleased(e: MouseEvent) {
          handle(e)
        }
      })
    }
    return inline
  }

  internal fun triggerPopupShowing() {
    if (popup != null && popup!!.isShowing) {
      hideProcessPopup()
    }
    else {
      FeatureUsageTracker.getInstance().triggerFeatureUsed("bg.progress.window.show.from.status.bar")
      openProcessPopup(true)
    }
  }

  private fun updateProgressIcon() {
    val progressIcon = mainPanel.inlinePanel.progressIcon
    if (originals.isEmpty() || PowerSaveMode.isEnabled()) {
      progressIcon.suspend()
    }
    else {
      progressIcon.resume()
    }
  }

  var isProcessWindowOpen: Boolean
    get() = popup != null && popup!!.isShowing
    set(open) {
      if (open) {
        openProcessPopup(true)
      }
      else {
        hideProcessPopup()
      }
    }

  override fun uiSettingsChanged(uiSettings: UISettings) {
    mainPanel.uiSettingsChanged(uiSettings)
  }

  private class InfoAndProgressPanelImpl(private val host: InfoAndProgressPanel) : JBPanel<JBPanel<*>?>(), UISettingsListener {
    val refreshIcon: JLabel = JLabel(AnimatedIcon.FS())
    val statusPanel: StatusPanel = StatusPanel()
    private val refreshAndInfoPanel = JPanel()
    val inlinePanel: InlineProgressPanel = InlineProgressPanel(host)
    private var centralComponent: JComponent? = null

    // see also: `VfsRefreshIndicatorWidgetFactory#myAvailable`
    var showNavBar: Boolean

    init {
      refreshIcon.isVisible = false
      setOpaque(false)
      setBorder(JBUI.Borders.empty())
      refreshAndInfoPanel.setLayout(BorderLayout())
      refreshAndInfoPanel.setOpaque(false)
      showNavBar = ExperimentalUI.isNewUI() && getInstance().showNavigationBarInBottom
      if (!showNavBar) {
        refreshAndInfoPanel.add(refreshIcon, BorderLayout.WEST)
        refreshAndInfoPanel.add(statusPanel, BorderLayout.CENTER)
      }
      setRefreshHidden()
      setLayout(InlineLayout())
      add(refreshAndInfoPanel)
      val wrapper = NonOpaquePanel(BorderLayout())
      wrapper.add(inlinePanel, BorderLayout.CENTER)
      wrapper.border = JBUI.Borders.empty(0, 40, 0, 4)
      add(wrapper)
      refreshAndInfoPanel.revalidate()
      refreshAndInfoPanel.repaint()
    }

    override fun uiSettingsChanged(uiSettings: UISettings) {
      val showNavBar = ExperimentalUI.isNewUI() && uiSettings.showNavigationBarInBottom
      if (showNavBar == this.showNavBar) {
        return
      }

      this.showNavBar = showNavBar
      val layout = refreshAndInfoPanel.layout as BorderLayout
      var c = layout.getLayoutComponent(BorderLayout.CENTER)
      if (c != null) {
        refreshAndInfoPanel.remove(c)
      }
      c = layout.getLayoutComponent(BorderLayout.WEST)
      if (c != null) {
        refreshAndInfoPanel.remove(c)
      }
      if (showNavBar) {
        val centralComponent = centralComponent
        if (centralComponent != null) {
          host.coroutineScope.launch(Dispatchers.EDT) {
            refreshAndInfoPanel.add(centralComponent, BorderLayout.CENTER)
            centralComponent.updateUI()
          }
        }
      }
      else {
        refreshAndInfoPanel.add(refreshIcon, BorderLayout.WEST)
        refreshAndInfoPanel.add(statusPanel, BorderLayout.CENTER)
        refreshIcon.updateUI()
        statusPanel.updateUI()
      }
    }

    fun setRefreshHidden() {
      if (showNavBar) {
        VfsRefreshIndicatorWidgetFactory.stop(host.statusBar)
      }
      else {
        refreshIcon.isVisible = false
      }
    }

    fun setCentralComponent(component: JComponent?) {
      if (showNavBar) {
        val c = (refreshAndInfoPanel.layout as BorderLayout).getLayoutComponent(BorderLayout.CENTER)
        if (c != null) {
          refreshAndInfoPanel.remove(c)
          centralComponent = null
        }
        if (component != null) {
          refreshAndInfoPanel.add(component, BorderLayout.CENTER)
        }
      }
      centralComponent = component
    }

    fun updateNavBarAutoscrollToSelectedLimit(autoscrollLimit: AutoscrollLimit) {
      val centralComponent = centralComponent
      if (centralComponent is ScrollableToSelected) {
        centralComponent.updateAutoscrollLimit(autoscrollLimit)
      }
    }

    fun removeProgress(progress: MyProgressComponent, last: Boolean) {
      if (last) {
        updateNavBarAutoscrollToSelectedLimit(AutoscrollLimit.UNLIMITED)
        inlinePanel.updateState(null)
        if (host.shouldClosePopupAndOnProcessFinish) {
          host.popup?.setHideOnFocusLost(true)
        }
      }
      else if (inlinePanel.indicator != null && inlinePanel.indicator!!.info === progress.info) {
        host.setInlineProgressByWeight()
      }
      else {
        inlinePanel.updateState()
      }
    }

    fun updateProgress(compact: MyProgressComponent) {
      inlinePanel.updateProgress(compact)
    }

    fun updateProgressState(delegate: MyProgressComponent?) {
      inlinePanel.updateState(delegate)
    }

    fun updateProgressState(showPopup: Boolean) {
      inlinePanel.updateState(showPopup)
    }

    override fun removeNotify() {
      super.removeNotify()

      if (ScreenUtil.isStandardAddRemoveNotify(this)) {
        GuiUtils.removePotentiallyLeakingReferences(refreshIcon)
      }
    }
  }

  private inner class ProgressPanelProgressComponent(task: TaskInfo, original: ProgressModel) :
    MyProgressComponent(compact = false, task = task, progressModel = original) {
    private val progressPanel = ProgressPanel.getProgressPanel(progress)!!
    private val cancelButton: InplaceButton?
    private val suspendButton: InplaceButton?
    private val suspendUpdateRunnable: Runnable

    override var processNameValue: @NlsContexts.ProgressTitle String?
      get() = progressPanel.labelText
      set(value) {
        progressPanel.setLabelText(value)
      }

    override var textValue: @NlsContexts.DetailedDescription String?
      get() = progressPanel.getCommentText()
      set(value) {
        progressPanel.setCommentText(value)
      }

    override var text2Value: @NlsContexts.DetailedDescription String?
      get() = super.text2Value
      set(value) {
        progressPanel.setText2(value)
      }

    init {
      ClientProperty.put(component, ProcessPopup.KEY, progressPanel)
      cancelButton = progressPanel.getCancelButton()!!
      cancelButton.setPainting(task.isCancellable())
      suspendButton = progressPanel.getSuspendButton()!!
      suspendUpdateRunnable = createSuspendUpdateRunnable(suspendButton)
      processNameValue = task.getTitle()

      // TODO: update javadoc for ProgressIndicator
    }

    override fun createSuspendUpdateRunnable(suspendButton: InplaceButton): Runnable {
      suspendButton.isVisible = false
      return Runnable {
        val suspender = suspender
        suspendButton.isVisible = suspender != null
        if (suspender != null && progressPanel.getState() == ProgressPanel.State.PAUSED != suspender.isSuspended) {
          progressPanel.setState(if (suspender.isSuspended) ProgressPanel.State.PAUSED else ProgressPanel.State.PLAYING)
          updateProgressIcon()
        }
      }
    }

    override fun canCheckPowerSaveMode(): Boolean = false

    override fun createComponent(): JPanel {
      val builder = ProgressPanelBuilder(progress).withTopSeparator()
      builder.withText2()
      builder.withCancel(Runnable { cancelRequest() })
      val suspendRunnable = createSuspendRunnable()
      builder.withPause(suspendRunnable).withResume(suspendRunnable)
      return builder.createPanel()
    }

    override fun setTextEnabled(value: Boolean) {
      progressPanel.setCommentEnabled(value)
    }

    override fun setText2Enabled(value: Boolean) {
      progressPanel.setText2Enabled(value)
    }

    override fun updateProgressNow() {
      super.updateProgressNow()
      suspendUpdateRunnable.run()
      updateCancelButton(suspendButton!!, cancelButton!!)
    }
  }

  internal open inner class MyProgressComponent(compact: Boolean, task: TaskInfo, progressModel: ProgressModel)
    : ProgressComponent(compact, task, progressModel), TitledIndicator {
    private var original: ProgressModel?
    internal val visibleInStatusBar: Boolean
      get() = indicatorModel.visibleInStatusBar

    override fun getText(): @NlsContexts.ProgressText String? {
      val text = (indicatorModel.getText() ?: "")
      val suspender = suspender
      return if (suspender != null && suspender.isSuspended) suspender.suspendedText else text
    }

    @JvmField
    var presentationModeProgressPanel: PresentationModeProgressPanel? = null

    @JvmField
    var presentationModeBalloon: Balloon? = null

    @JvmField
    var presentationModeShowBalloon: Boolean = false

    constructor(task: TaskInfo, original: ProgressModel) : this(compact = true, task = task, progressModel = original)

    init {
      this.original = progressModel
    }

    override fun createCompactTextAndProgress(component: JPanel) {
      textPanel.setTextAlignment(Component.RIGHT_ALIGNMENT)
      textPanel.recomputeSize()
      super.createCompactTextAndProgress(component)
      (progress.parent as JComponent).setBorder(JBUI.Borders.empty(0, 8, 0, 4))
    }

    override fun wrapProgress(): JComponent {
      val progressWrapper = if (isCompact) {
        object : NonOpaquePanel(BorderLayout()) {
          override fun getPreferredSize(): Dimension {
            val insets = border.getBorderInsets(this)
            return Dimension(JBUI.scale(104 + insets.width), -1)
          }
        }
      }
      else {
        NonOpaquePanel(BorderLayout())
      }
      progressWrapper.add(progress, BorderLayout.CENTER)
      return progressWrapper
    }

    override fun onFinish() {
      removeProgressRequests.queue(this@MyProgressComponent)
    }

    open fun canCheckPowerSaveMode(): Boolean = true

    override fun createEastButtons(): List<ProgressButton> {
      return listOf(createSuspendButton()) + super.createEastButtons()
    }

    protected fun updateCancelButton(suspend: InplaceButton, cancel: InplaceButton) {
      val painting = info.isCancellable() && !isStopping
      cancel.setPainting(painting)
      cancel.isVisible = painting || !suspend.isVisible
    }

    fun createPresentationButtons(): JBIterable<ProgressButton> {
      val suspend = createSuspendButton()
      val cancel = createCancelButton()
      return JBIterable.of(suspend).append(ProgressButton(cancel.button,
                                                          Runnable {
                                                            updateCancelButton(suspend.button, cancel.button)
                                                          }))
    }

    private fun createSuspendButton(): ProgressButton {
      val suspendButton = InplaceButton("", AllIcons.Actions.Pause, ActionListener { createSuspendRunnable().run() }).setFillBg(false)
      return ProgressButton(suspendButton, createSuspendUpdateRunnable(suspendButton))
    }

    protected fun createSuspendRunnable(): Runnable {
      return Runnable {
        val suspender = suspender
        if (suspender == null) {
          return@Runnable
        }
        if (suspender.isSuspended) {
          suspender.resumeProcess()
        }
        else {
          suspender.suspendProcess(null)
        }
        setInlineProgressByWeight()
        (if (suspender.isSuspended) ProgressPaused else ProgressResumed).log()
      }
    }

    protected open fun createSuspendUpdateRunnable(suspendButton: InplaceButton): Runnable {
      suspendButton.isVisible = false
      return Runnable {
        val suspender = suspender
        suspendButton.isVisible = suspender != null
        if (suspender != null) {
          val toolTipText = if (suspender.isSuspended) IdeBundle.message("comment.text.resume")
          else IdeBundle.message("comment.text.pause")
          if (toolTipText != suspendButton.toolTipText) {
            updateProgressIcon()
            if (suspender.isSuspended) {
              showResumeIcons(suspendButton)
            }
            else {
              showPauseIcons(suspendButton)
            }
            suspendButton.setToolTipText(toolTipText)
          }
        }
      }
    }

    private fun showPauseIcons(button: InplaceButton) {
      setIcons(button, AllIcons.Process.ProgressPauseSmall, AllIcons.Process.ProgressPause, AllIcons.Process.ProgressPauseSmallHover,
               AllIcons.Process.ProgressPauseHover)
    }

    private fun showResumeIcons(button: InplaceButton) {
      setIcons(button, AllIcons.Process.ProgressResumeSmall, AllIcons.Process.ProgressResume, AllIcons.Process.ProgressResumeSmallHover,
               AllIcons.Process.ProgressResumeHover)
    }

    private fun setIcons(button: InplaceButton, compactRegular: Icon, regular: Icon, compactHovered: Icon, hovered: Icon) {
      button.setIcons(if (isCompact) compactRegular else regular, null, if (isCompact) compactHovered else hovered)
      button.revalidate()
      button.repaint()
    }

    protected val suspender: ProgressSuspender?
      get() {
        return ProgressSuspender.getSuspender(original?.getProgressIndicator() ?: return null)
      }

    override val isFinished: Boolean = indicatorModel.isFinished(info)

    override fun dispose() {
      super.dispose()
      original = null
    }

    override fun cancelRequest() {
      original!!.cancel()
    }

    override fun queueProgressUpdate() {
      synchronized(dirtyIndicators) { dirtyIndicators.add(this) }
      check(updateRequests.tryEmit(Unit))
    }

    override fun updateProgressNow() {
      progress.isVisible = !PowerSaveMode.isEnabled() || !isPaintingIndeterminate
      super.updateProgressNow()
      if (presentationModeProgressPanel != null) presentationModeProgressPanel!!.update()
    }

    fun showInPresentationMode(): Boolean {
      return !isProcessWindowOpen
    }

    override fun setTitle(title: @NlsContexts.ProgressTitle String) {
      processNameValue = title
    }

    override fun getTitle(): @NlsContexts.ProgressTitle String? {
      return processNameValue
    }

    internal fun setHoveredEffectForText(hovered: Boolean) {
      textPanel.setHoveredEffectForText(hovered)
    }
  }

  private fun runQuery() {
    val indicators = inlineToOriginal.keys
    if (indicators.isEmpty()) {
      return
    }

    for (each in indicators) {
      each.queueProgressUpdate()
    }
    check(runQueryRequests.tryEmit(Unit))
  }

  private class InlineLayout : AbstractLayoutManager() {
    override fun preferredLayoutSize(parent: Container): Dimension {
      val result = Dimension()
      val count = parent.componentCount
      for (i in 0 until count) {
        val size = parent.getComponent(i).preferredSize
        result.width += size.width
        result.height = max(result.height, size.height)
      }
      return result
    }

    override fun layoutContainer(parent: Container) {
      if (parent.componentCount != 2) {
        return  // e.g., project frame is closed
      }
      val infoPanel = parent.getComponent(0)
      val progressPanel = parent.getComponent(1)
      val size = parent.size
      val progressWidth = progressPanel.preferredSize.width
      if (progressWidth > size.width) {
        infoPanel.setBounds(0, 0, 0, 0)
        progressPanel.setBounds(0, 0, size.width, size.height)
      }
      else {
        infoPanel.setBounds(0, 0, size.width - progressWidth, size.height)
        progressPanel.setBounds(size.width - progressWidth, 0, progressWidth, size.height)
      }
    }
  }

  private class InlineProgressPanel(private val host: InfoAndProgressPanel) : NonOpaquePanel() {
    companion object {
      private val gap: Int
        get() = JBUI.scale(10)

      @Suppress("NAME_SHADOWING")
      private fun setBounds(component: JComponent?, x: Int, centerY: Int, size: Dimension?, minusWidth: Boolean): Int {
        var x = x
        var size = size
        if (size == null) {
          size = component!!.getPreferredSize()
        }
        if (minusWidth) {
          x -= size!!.width
        }
        component!!.setBounds(x, centerY - size!!.height / 2, size.width, size.height)
        return x
      }
    }

    val progressIcon: AsyncProcessIcon = AsyncProcessIcon(host.coroutineScope)
    var indicator: MyProgressComponent? = null
    private val multiProcessLink = TextPanel()
    private val counterComponent: CounterLabel
    private var isHovered = false
    init {
      border = JBUI.CurrentTheme.StatusBar.Widget.border()
      progressIcon.setOpaque(false)
      progressIcon.addMouseListener(object : MouseAdapter() {
        override fun mousePressed(e: MouseEvent) {
          host.handle(e)
        }

        override fun mouseReleased(e: MouseEvent) {
          host.handle(e)
        }
      })
      updateProgressIconBorder()
      val listener = object : RegistryValueListener {
        override fun afterValueChanged(value: RegistryValue) {
          updateProgressIconBorder()
        }
      }
      Registry.get(SHOW_COUNTER_REGISTRY_KEY).addListener(listener, host.coroutineScope)
      multiProcessLink.addMouseListener(object : MouseAdapter() {
        override fun mouseClicked(e: MouseEvent) {
          host.triggerPopupShowing()
        }
      })

      counterComponent = CounterLabel()
      counterComponent.addMouseListener(object : MouseAdapter() {
        override fun mouseClicked(e: MouseEvent) {
          host.triggerPopupShowing()
        }
      })
      addMouseHoverListener(host.lifecycleDisposable, object : HoverListener() {
        override fun mouseEntered(component: Component, x: Int, y: Int) {
          setHovered(true)
        }

        override fun mouseMoved(component: Component, x: Int, y: Int) {
        }

        override fun mouseExited(component: Component) {
          setHovered(false)
        }

        private fun setHovered(hovered: Boolean) {
          isHovered = hovered
          counterComponent.setHoveredEffectForText(hovered)
          indicator?.textPanel?.setHoveredEffectForText(hovered)
          repaint()
        }
      })

      setLayout(object : AbstractLayoutManager() {
        override fun preferredLayoutSize(parent: Container): Dimension {
          val result = Dimension()
          fun addVisibleToPreferred(component: JComponent, withGap: Boolean) {
            if (component.isVisible) {
              if (withGap && result.width > 0) {
                result.width += gap
              }
              val size = component.getPreferredSize()
              result.width += size.width
              result.height = max(result.height, size.height)
            }
          }

          if (indicator != null) {
            addVisibleToPreferred(indicator!!.component, withGap = false)
          }
          addVisibleToPreferred(multiProcessLink, withGap = true)

          if (showCounterInsteadOfMultiProcessLink) {
            addVisibleToPreferred(counterComponent, withGap = false)
            addVisibleToPreferred(progressIcon, withGap = false)
          }

          if (progressIcon.isVisible) {
            result.height = max(result.height, progressIcon.getPreferredSize().height)
          }
          JBInsets.addTo(result, parent.insets)
          return result
        }

        override fun layoutContainer(parent: Container) {
          if (indicator == null) {
            progressIcon.isVisible = false
            counterComponent.isVisible = false
            return
          }
          val insets = parent.insets
          val x = insets.left
          val centerY = (parent.height + insets.top - insets.bottom) / 2
          val width = parent.width - insets.left - insets.right
          var rightX = parent.width - insets.right
          val gap = gap
          val indicatorComponent = indicator!!.component
          if (indicatorComponent.isVisible) {
            var preferredWidth = preferredLayoutSize(parent).width - insets.left - insets.right
            var indicatorSize: Dimension? = null
            if (preferredWidth > width) {
              val progressWidth2x = this@InlineProgressPanel.indicator!!.progress.getPreferredSize().width * 2
              if (width > progressWidth2x && this@InlineProgressPanel.indicator!!.textPanel.getPreferredSize().width > progressWidth2x) {
                preferredWidth = width
                indicatorSize = Dimension(width, indicatorComponent.getPreferredSize().height)
                if (multiProcessLink.isVisible) {
                  indicatorSize.width -= multiProcessLink.getPreferredSize().width + gap
                }
              }
            }
            if (preferredWidth > width) {
              layoutWithUnfittingIndicator(indicatorComponent,
                                           initialPreferredWidth = preferredWidth,
                                           initialRightX = rightX,
                                           centerY = centerY)
            }
            else {
              layoutWithFittingIndicator(indicatorComponent,
                                         initialIndicatorSize = indicatorSize,
                                         initialRightX = rightX,
                                         centerY = centerY)
            }
          }
          else {
            layoutWithoutIndicator(initialX = x, initialRightX = rightX, centerY = centerY)
          }
        }

        private fun layoutWithUnfittingIndicator(indicatorComponent: JPanel, initialPreferredWidth: Int, initialRightX: Int, centerY: Int) {
          var preferredWidth = initialPreferredWidth
          var rightX = initialRightX
          indicatorComponent.setBounds(0, 0, 0, 0)

          if (showCounterInsteadOfMultiProcessLink && counterComponent.isVisible) {
            rightX = setBounds(counterComponent, rightX, centerY, null, true)

            progressIcon.isVisible = true
            setBounds(progressIcon, rightX, centerY, null, true)
            return
          }
          // With showCounterInsteadOfMultiProcessLink and !counterComponent.isVisible (single progress)
          // behavior is just the same as with !showCounterInsteadOfMultiProcessLink

          progressIcon.isVisible = true
          val iconSize = progressIcon.getPreferredSize()
          preferredWidth = iconSize.width
          if (multiProcessLink.isVisible) {
            preferredWidth += gap + multiProcessLink.getPreferredSize().width
          }
          if (preferredWidth > width) {
            if (multiProcessLink.isVisible) {
              multiProcessLink.setBounds(0, 0, 0, 0)
            }
            setBounds(progressIcon, 0, centerY, iconSize, false)
          }
          else {
            var miniWidth = true
            if (multiProcessLink.isVisible) {
              rightX = setBounds(multiProcessLink, rightX, centerY, null, true) - gap
            }
            else if (width < 60) {
              rightX = 0
              miniWidth = false
            }
            setBounds(progressIcon, rightX, centerY, iconSize, miniWidth)
          }
        }

        private fun layoutWithFittingIndicator(indicatorComponent: JPanel, initialIndicatorSize: Dimension?, initialRightX: Int, centerY: Int) {
          var rightX = initialRightX
          var indicatorSize = initialIndicatorSize
          progressIcon.isVisible = false

          when {
            showCounterInsteadOfMultiProcessLink && counterComponent.isVisible -> {
              rightX = setBounds(counterComponent, rightX, centerY, null, true)
            }
            multiProcessLink.isVisible /* && !showCounterInsteadOfMultiProcessLink */ -> {
              rightX = setBounds(multiProcessLink, rightX, centerY, null, true) - gap
            }
            //for single progress with `showCounterInsteadOfMultiProcessLink == false` do nothing, see IJPL-192911
          }
          setBounds(indicatorComponent, rightX, centerY, indicatorSize, true)
        }

        private fun layoutWithoutIndicator(initialRightX: Int, centerY: Int, initialX: Int) {
          var rightX = initialRightX
          if (showCounterInsteadOfMultiProcessLink) {
            if (counterComponent.isVisible) {
              rightX = setBounds(counterComponent, rightX, centerY, null, true)
            }
            if (progressIcon.isVisible) {
              rightX = setBounds(progressIcon, rightX, centerY, null, true)
            }
            if (multiProcessLink.isVisible) {
              rightX = setBounds(multiProcessLink, rightX, centerY, null, true)
            }
            return
          }
          val linkSize = multiProcessLink.getPreferredSize()
          val preferredWidth = linkSize.width
          if (preferredWidth > width) {
            multiProcessLink.setBounds(0, 0, 0, 0)
            progressIcon.isVisible = true
            setBounds(progressIcon, initialX, centerY, null, false)
          }
          else {
            progressIcon.isVisible = false
            setBounds(multiProcessLink, rightX, centerY, linkSize, true)
          }
        }
      })
      add(progressIcon)
      progressIcon.isVisible = false
      add(multiProcessLink)
      multiProcessLink.isVisible = false
      add(counterComponent)
      counterComponent.isVisible = false
    }

    private fun updateProgressIconBorder() {
      if (showCounterInsteadOfMultiProcessLink) {
        progressIcon.setBorder(JBUI.Borders.empty())
      }
      else {
        progressIcon.setBorder(JBUI.CurrentTheme.StatusBar.Widget.border())
      }
    }

    fun updateProgress(compact: MyProgressComponent?) {
      if (indicator == null) {
        updateState(compact)
      }
      else {
        host.setInlineProgressByWeight()
      }
    }

    fun updateState(indicator: MyProgressComponent?) {
      if (rootPane == null) {
        return  // e.g., project frame is closed
      }
      if (this.indicator != null) {
        if (this.indicator === indicator) {
          updateState()
          return
        }
        remove(this.indicator!!.component)
        this.indicator?.setHoveredEffectForText(false)
      }
      this.indicator = indicator
      this.indicator?.setHoveredEffectForText(isHovered)
      if (indicator == null) {
        multiProcessLink.isVisible = false
        doLayout()
        revalidate()
        repaint()
      }
      else {
        add(indicator.component)
        updateState()
      }
    }

    @JvmOverloads
    fun updateState(showPopup: Boolean = host.popup != null && host.popup!!.isShowing) {
      val currentIndicator = indicator
      if (currentIndicator == null) {
        return
      }
      val size = host.originals.size
      val isIndicatorVisible = !showPopup && (!supportSecondaryProgresses || currentIndicator.visibleInStatusBar)
      currentIndicator.component.isVisible = isIndicatorVisible
      if (showCounterInsteadOfMultiProcessLink) {
        counterComponent.setNumber(size, isIndicatorVisible, showPopup)
        counterComponent.isVisible = true
        progressIcon.isVisible = !showPopup && !isIndicatorVisible
        multiProcessLink.text = IdeBundle.message("link.hide.processes", size)
        multiProcessLink.isVisible = showPopup
      }
      else {
        counterComponent.isVisible = false
        progressIcon.isVisible = false
        multiProcessLink.isVisible = showPopup || size > 1
        if (showPopup) {
          multiProcessLink.text = IdeBundle.message("link.hide.processes", size)
        }
        else if (size > 1) {
          multiProcessLink.text = IdeBundle.message("link.show.all.processes", size)
        }
      }

      val tooltip = ProgressComponent.computeTooltipText(if (isIndicatorVisible) currentIndicator.indicatorModel else null)
      progressIcon.toolTipText = tooltip
      counterComponent.toolTipText = tooltip
      doLayout()
      revalidate()
      repaint()
    }

    override fun paintComponent(g: Graphics) {
      if (isHovered && indicator != null) {
        val statusBar = this.getParentOfType<StatusBar>()
        if (statusBar != null) {
          val bounds = bounds
          bounds.setLocation(0, 0)
          bounds.width -= counterComponent.getWidthAdditionForAlignmemt()
          IdeStatusBarImpl.paintHover(g, this, bounds, JBUI.CurrentTheme.StatusBar.Widget.HOVER_BACKGROUND, statusBar)
        }
      }
      super.paintComponent(g)
    }
  }
}

private class CounterLabel : JPanel(), UISettingsListener {
  private val textPanel: TextPanel
  private var numberOfProgresses: Int = 0
  private var isProgressVisible: Boolean = false
  private var isPopupShowing: Boolean = false

  private var lastDigitNumber: Int = 0
  private var textsForMinimumSize: IntRange? = null

  private var minimumSize: Dimension? = null

  init {
    textPanel = createTextPanel()
    layout = BorderLayout()
    add(textPanel, BorderLayout.CENTER)
    isOpaque = false
    setNumber(0, isProgressVisible = false, isPopupShowing = false)
  }

  private fun createTextPanel(): TextPanel {
    val panel = TextPanel()
    panel.foreground = JBUI.CurrentTheme.StatusBar.Widget.FOREGROUND
    panel.border = JBUI.Borders.emptyLeft(4)
    return panel
  }


  fun setNumber(numberOfProgresses: Int, isProgressVisible: Boolean, isPopupShowing: Boolean) {
    this.numberOfProgresses = numberOfProgresses
    this.isProgressVisible = isProgressVisible
    this.isPopupShowing = isPopupShowing

    refreshTextForMinimumSizeIfNeeded()
    refreshPanelText(textPanel)
  }

  private fun getNumberToShow(): Int {
    return if (isProgressVisible) {
      numberOfProgresses - 1
    }
    else {
      numberOfProgresses
    }
  }

  private fun refreshPanelText(panel: TextPanel) {
    val numberToShow = getNumberToShow()
    val text = when {
      isPopupShowing -> ""
      numberToShow <= 0 -> ""
      !isProgressVisible && numberToShow == 1 -> ""
      isProgressVisible -> "+${numberToShow}"
      else -> "${numberToShow}"
    }
    panel.text = text
  }

  private fun refreshTextForMinimumSizeIfNeeded() {
    val numberToShowString = getNumberToShow().toString()
    if (numberToShowString.length != lastDigitNumber) {
      lastDigitNumber = numberToShowString.length
      textsForMinimumSize = when (lastDigitNumber) {
        1 -> 0..9
        2 -> 10..99
        3 -> 100..999
        else -> null
      }
      minimumSize = null
    }
  }

  fun setHoveredEffectForText(hovered: Boolean) {
    textPanel.setHoveredEffectForText(hovered)
  }

  override fun getPreferredSize(): Dimension {
    val precalculated = minimumSize
    if (precalculated != null) return precalculated

    val texts = textsForMinimumSize
    if (texts == null || texts.isEmpty()) return super.getPreferredSize()

    val probe = createTextPanel()

    var minSize = Dimension(0, 0)
    for (text in texts) {
      probe.text = "+$text"
      val size = probe.preferredSize
      minSize = Dimension(max(minSize.width, size.width), max(minSize.height, size.height))
    }
    minimumSize = minSize

    return minSize
  }

  override fun getMinimumSize(): Dimension {
    return preferredSize
  }

  override fun uiSettingsChanged(uiSettings: UISettings) {
    minimumSize = null
  }

  fun getWidthAdditionForAlignmemt(): Int {
    if (!isVisible) return 0
    val minWidth = getPreferredSize().width
    val panel = createTextPanel()
    refreshPanelText(panel)
    return minWidth - panel.preferredSize.width
  }
}

// In TextPanel only text is painted by the component. The color of the text is defined by effect,
// and not by foreground property.
// TextPanel is not opaque, and a background for hovered effect is provided by [IdeStatusBarImpl]
private fun TextPanel.setHoveredEffectForText(hovered: Boolean) {
  val value = if (hovered) IdeStatusBarImpl.WidgetEffect.HOVER else null
  putClientProperty(WIDGET_EFFECT_KEY, value)
}