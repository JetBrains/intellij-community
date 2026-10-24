// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.icons.inspector.model

import com.intellij.devkit.icons.inspector.DevkitIconsInspectorBundle
import com.intellij.devkit.icons.inspector.model.InspectorFormatting.simpleClassName
import com.intellij.devkit.icons.inspector.picker.IconPicker
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.icons.IconDescriptor
import com.intellij.platform.icons.impl.intellij.rendering.SwingIcon
import com.intellij.platform.icons.impl.rendering.debug.IconRenderBreakpoint
import com.intellij.platform.icons.impl.rendering.debug.IconRenderDebugger
import com.intellij.platform.icons.impl.rendering.debug.RenderCapture
import com.intellij.platform.icons.impl.rendering.debug.RenderNode
import com.intellij.platform.icons.swing.toIconDescriptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.awt.Component
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicReferenceArray
import com.intellij.openapi.actionSystem.impl.ActionButton
import javax.swing.AbstractButton
import javax.swing.Icon
import javax.swing.JLabel
import javax.swing.SwingUtilities

private val LOG = logger<IconInspectorControllerImpl>()

/** Minimum delay between two UI updates caused by live paints / hits (≈ 4 updates per second). */
private const val UPDATE_THROTTLE_MS = 250L

/** How often an applied edited icon is checked for being replaced by the component. */
private const val REPLACEMENT_CHECK_MS = 500

/** How long to wait for the picked icon to be painted before falling back to a static tree. */
private const val CAPTURE_TIMEOUT_MS = 1000L

/**
 * Implementation of [IconInspectorController]: picker → component icon → render capture → inspector tree, plus the bridge to
 * [IconRenderDebugger] flags.
 *
 * Threading: all public methods must be called on the EDT. [state] and [flagged] are updated on the EDT; capture and hit
 * notifications arriving on rendering threads are marshalled to the EDT and throttled.
 */
@Service(Service.Level.PROJECT)
internal class IconInspectorControllerImpl(
  @Suppress("unused") private val project: Project,
  private val coroutineScope: CoroutineScope,
) : IconInspectorController, Disposable {
  private val _state = MutableStateFlow<InspectorState>(InspectorState.Empty)
  override val state: StateFlow<InspectorState> = _state.asStateFlow()

  private val _modifierEditing = MutableStateFlow<ModifierEditingState?>(null)
  override val modifierEditing: StateFlow<ModifierEditingState?> = _modifierEditing.asStateFlow()

  private val _flagged = MutableStateFlow<List<FlaggedRenderer>>(emptyList())
  override val flagged: StateFlow<List<FlaggedRenderer>> = _flagged.asStateFlow()

  override val breakpointExceptionClass: String = IconRenderBreakpoint::class.java.name

  private val edt = Dispatchers.EDT + ModalityState.any().asContextElement()

  private var picker: IconPicker? = null
  private var stateBeforePicking: InspectorState = InspectorState.Empty
  private var session: Session? = null
  private var generation = 0
  private var disposed = false

  /** Stability of the current pick; survives [refresh], restarts on a new pick. */
  private var stability = RendererStabilityTracker()

  /** Renderers flagged through this controller, by node id (ids are unique across inspections). EDT only. */
  private val flaggedEntries = LinkedHashMap<String, FlagEntry>()

  /** Copy of the flagged renderer instances, readable from rendering threads. */
  @Volatile
  private var flaggedRenderersSnapshot: List<Any> = emptyList()

  private val hitSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  private val hitListener: AutoCloseable

  init {
    hitListener = IconRenderDebugger.addHitListener { hit ->
      // rendering thread: only signal, the EDT reads hit counts
      if (hit.renderer.let { renderer -> flaggedRenderersSnapshot.any { it === renderer } }) {
        hitSignal.tryEmit(Unit)
      }
    }
    coroutineScope.launch(edt) {
      hitSignal.collect {
        publishFlagged()
        delay(UPDATE_THROTTLE_MS)
      }
    }
  }

  // ---- picking ----

  override fun startPicking() {
    if (disposed) return
    if (!ApplicationManager.getApplication().isInternal) {
      _state.value = InspectorState.Error(DevkitIconsInspectorBundle.message("model.error.internal.only"))
      return
    }
    picker?.let { Disposer.dispose(it) }
    val current = _state.value
    stateBeforePicking = if (current is InspectorState.Picking) stateBeforePicking else current
    val newPicker = IconPicker(
      onPicked = { picked ->
        picker = null
        inspect(picked, newGeneration = true)
      },
      onCancelled = {
        picker = null
        if (_state.value is InspectorState.Picking) _state.value = stateBeforePicking
      },
    )
    picker = newPicker
    Disposer.register(this, newPicker)
    _state.value = InspectorState.Picking
    try {
      newPicker.start()
    }
    catch (t: Throwable) {
      LOG.error(t)
      Disposer.dispose(newPicker)
      _state.value = InspectorState.Error(t.toString())
    }
  }

  override fun cancelPicking() {
    picker?.cancel()
    picker = null
    if (_state.value is InspectorState.Picking) _state.value = stateBeforePicking
  }

  // ---- inspection ----

  /** Inspects [picked]; exposed for tests. */
  internal fun inspect(picked: PickedIcon, newGeneration: Boolean) {
    if (disposed) return
    if (newGeneration) {
      generation++
      stability = RendererStabilityTracker()
    }
    closeSession()
    val newSession = Session(generation, picked)
    session = newSession
    newSession.publishInitial()
    newSession.open()
  }

  override fun refresh() {
    val current = session ?: return
    val component = current.component ?: run {
      clear()
      return
    }
    val picked = current.picked
    val repicked = if (picked.compose != null) picked
    else try {
      ComponentIconExtractor.extract(component, picked.point)
    }
    catch (t: Throwable) {
      LOG.debug(t)
      null
    } ?: picked
    inspect(repicked, newGeneration = false)
  }

  override fun clear() {
    picker?.let { Disposer.dispose(it) }
    picker = null
    closeSession()
    _state.value = InspectorState.Empty
  }

  private fun closeSession() {
    session?.close()
    session = null
    _modifierEditing.value = null
  }

  // ---- modifier editing ----

  override fun editModifiers(edit: ModifierEdit): String? {
    val current = session ?: return "No icon is inspected"
    return current.edit(edit)
  }

  override fun setAppliedToComponent(applied: Boolean) {
    session?.setApplied(applied)
  }

  override fun revertModifierEdits() {
    val current = session ?: return
    current.restoreComponent()
    // a fresh inspection of the (restored) icon
    inspect(current.picked, newGeneration = false)
  }

  override fun resetStability() {
    stability.reset()
    session?.republishStability()
  }

  // ---- flags ----

  override fun setFlagged(nodeId: String, flagged: Boolean) {
    if (flagged) {
      val current = session ?: return
      val renderer = current.renderers[nodeId] ?: return
      val existing = flaggedEntries[nodeId]
      if (existing != null) {
        if (existing.renderer.get() === renderer) return
        // the node's renderer was replaced since it was flagged: move the flag to the current instance
        removeEntry(nodeId)
      }
      val title = current.titles[nodeId] ?: simpleClassName(renderer)
      IconRenderDebugger.flag(renderer)
      flaggedEntries[nodeId] = FlagEntry(nodeId, title, WeakReference(renderer))
    }
    else {
      removeEntry(nodeId)
    }
    publishFlagged()
  }

  private fun removeEntry(nodeId: String) {
    val entry = flaggedEntries.remove(nodeId) ?: return
    entry.renderer.get()?.let { renderer ->
      // another entry may flag the same instance (e.g. same node after refresh); keep it flagged then
      if (flaggedEntries.values.none { it.renderer.get() === renderer }) IconRenderDebugger.unflag(renderer)
    }
  }

  /** True only while the renderer CURRENTLY at [nodeId] is flagged (a flag on a replaced instance no longer fires). */
  override fun isFlagged(nodeId: String): Boolean {
    val renderer = session?.renderers?.get(nodeId) ?: return false
    return flaggedEntries.values.any { it.renderer.get() === renderer }
  }

  override fun clearFlags() {
    for (entry in flaggedEntries.values) {
      entry.renderer.get()?.let(IconRenderDebugger::unflag)
    }
    flaggedEntries.clear()
    publishFlagged()
  }

  private fun publishFlagged() {
    // drop entries whose renderer was garbage collected
    flaggedEntries.values.removeIf { it.renderer.get() == null }
    flaggedRenderersSnapshot = flaggedEntries.values.mapNotNull { it.renderer.get() }
    val current = session
    _flagged.value = flaggedEntries.values.mapNotNull { entry ->
      val renderer = entry.renderer.get() ?: return@mapNotNull null
      val replaced = current != null && current.isReplaced(entry.nodeId, renderer)
      val title = if (replaced) entry.title + DevkitIconsInspectorBundle.message("model.flag.replaced") else entry.title
      FlaggedRenderer(entry.nodeId, title, IconRenderDebugger.hitCount(renderer))
    }
  }

  override fun dispose() {
    disposed = true
    picker = null // disposed as a child
    closeSession()
    clearFlags()
    try {
      hitListener.close()
    }
    catch (t: Throwable) {
      LOG.warn(t)
    }
  }

  // ---- session ----

  private class FlagEntry(val nodeId: String, val title: String, val renderer: WeakReference<Any>)

  private enum class Mode {
    /** A [SwingIcon]: its own paints (and, for plain components, the paints of icons replacing it at the same place). */
    SWING,
    /** A legacy Swing icon: paints of the SwingIcons nested in it. */
    LEGACY,
    /** An icon inside a Compose host: the icons-API draws at the picked point. */
    COMPOSE,
  }

  /** One inspected target: its capture subscription, the current tree and the renderer instances behind node ids. EDT only. */
  private inner class Session(val generation: Int, val picked: PickedIcon) {
    private val componentRef = WeakReference(picked.component)
    private val scopeRef = WeakReference(picked.captureScope)
    private val icon: Icon? = picked.icon
    private val compose: ComposePick? = picked.compose
    private val mode: Mode = when {
      compose != null -> Mode.COMPOSE
      icon is SwingIcon -> Mode.SWING
      else -> Mode.LEGACY
    }
    val idPrefix = "i$generation:"
    private val bridged = mode == Mode.LEGACY
    private val descriptorResult: Result<IconDescriptor> = runCatching {
      when {
        compose != null -> compose.capture?.descriptor ?: error(DevkitIconsInspectorBundle.message("model.compose.not.icons.api"))
        icon is SwingIcon -> icon.iconDescriptor
        else -> icon!!.toIconDescriptor()
      }
    }
    private val descriptor: IconDescriptor? = descriptorResult.getOrNull()
    private val tracker: RendererStabilityTracker = stability
    /** Screen scale and the measured painted size of the picked icon (one offscreen repaint per inspection). */
    private val sizing: SizingContext by lazy {
      val measured = runCatching { PaintRecorder.measureWithFrame(picked) }.getOrNull()
      SizingContext(IconSizing.screenScale(picked.component), measured?.first, measured?.second)
    }
    /** The SVG each pipeline rasterizes for the picked icon (computed once per inspection, after [sizing]). */
    private val sources: List<ResolvedSvgSource> by lazy {
      try {
        SvgSources.compute(picked, sizing)
      }
      catch (t: Throwable) {
        LOG.warn(t)
        emptyList()
      }
    }

    val component: Component? get() = componentRef.get()

    /** Node id → renderer instance; empty for static trees. */
    var renderers: Map<String, Any> = emptyMap()
      private set

    /** Node id → title, for flagged-entry labels. */
    var titles: Map<String, String> = emptyMap()
      private set

    private var captureHandle: AutoCloseable? = null

    /**
     * LEGACY mode: descriptors whose paints are captured, the descriptors of the SwingIcons nested in the legacy structure
     * (those paint through `SwingIcon.paintIcon`; the legacy icon itself does not).
     */
    private val capturedDescriptors: List<IconDescriptor> =
      if (mode != Mode.LEGACY) emptyList()
      else picked.icon?.let { legacy ->
        runCatching { InspectorTreeBuilder.collectSwingIcons(legacy).map { it.iconDescriptor } }.getOrNull()
      } ?: emptyList()

    /** LEGACY mode: latest capture per entry of [capturedDescriptors]. */
    private val latestCaptures = AtomicReferenceArray<RenderCapture?>(capturedDescriptors.size)
    /** SWING / COMPOSE mode: latest accepted capture of the inspected icon. */
    private val latestCapture = AtomicReference<RenderCapture?>()
    /** SWING mode: first capture whose descriptor did not match the picked icon (e.g. a wrapper painting another SwingIcon). */
    private val fallbackCapture = AtomicReference<RenderCapture?>()
    private val captureSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val jobs = ArrayList<Job>()
    @Volatile
    private var closed = false

    private var captured = false
    private var structureKey: List<Any>? = null
    private var target: InspectionTarget? = null
    /** Tree of the last published captured target, for [republishStability]. */
    private var lastTree: InspectorTree? = null
    private var previews: List<PreviewImage>? = null

    // ---- modifier editing ----
    /** Root descriptor of the current tree (edits apply to it) and the addresses of its layer nodes. */
    private var editBase: IconDescriptor? = null
    private var layerAddresses: Map<String, LayerAddress> = emptyMap()
    private var edited: IconDescriptor? = null
    private var editCount = 0
    private var applied = false
    private var installedIcon: Icon? = null
    private var originalComponentIcon: Icon? = null
    private var replacements = 0
    private var editMessage: String? = null
    private var replacementTimer: javax.swing.Timer? = null
    /** The descriptor installed in the component; its paints are accepted as this icon's. Read on rendering threads. */
    @Volatile
    private var appliedDescriptor: IconDescriptor? = null

    fun publishInitial() {
      val pending = DevkitIconsInspectorBundle.message("model.summary.capture.pending")
      val initial = buildStaticTarget(pending)
      // captured modes are measuring until the first paint arrives
      publish(if (mode == Mode.LEGACY) initial else initial.copy(stability = tracker.snapshot()))
    }

    fun open() {
      val scope = picked.captureScope
      captureHandle = IconRenderDebugger.capture({ paintTarget -> accepts(paintTarget) }) { capture -> onCapture(capture) }
      jobs.add(coroutineScope.launch(edt) {
        captureSignal.collect {
          processCapture()
          delay(UPDATE_THROTTLE_MS)
        }
      })
      jobs.add(coroutineScope.launch(edt) {
        delay(CAPTURE_TIMEOUT_MS)
        if (!captured && !closed) onCaptureTimeout()
      })
      repaint(scope)
    }

    private fun repaint(scope: Component) {
      if (mode == Mode.COMPOSE) {
        // Compose caches drawings: a repaint of the host alone does not re-run the icon draws
        IconRenderDebugger.requestRedraw(scope)
        scope.repaint()
        return
      }
      val owner = componentRef.get()
      if (owner != null && owner !== scope) {
        // cell renderers are painted by their owner
        owner.repaint()
      }
      else {
        scope.repaint()
      }
    }

    // rendering thread: keep it cheap
    private fun accepts(paintTarget: Any?): Boolean {
      if (closed || paintTarget !is Component) return false
      val scope = scopeRef.get() ?: return false
      return paintTarget === scope || SwingUtilities.isDescendingFrom(paintTarget, scope)
    }

    // rendering thread: keep it cheap
    private fun onCapture(capture: RenderCapture) {
      if (closed) return
      when (mode) {
        Mode.COMPOSE -> {
          val info = capture.composePaintInfo ?: return
          if (info.contains(compose!!.scenePointX, compose.scenePointY)) accept(capture)
        }
        Mode.SWING -> when {
          appliedDescriptor != null && capture.descriptor === appliedDescriptor -> accept(capture)
          capture.descriptor === descriptor || capture.descriptor == descriptor -> accept(capture)
          isSuccessor(capture) -> accept(capture)
          else -> fallbackCapture.compareAndSet(null, capture)
        }
        Mode.LEGACY -> {
          var index = capturedDescriptors.indexOfFirst { it === capture.descriptor }
          if (index < 0) index = capturedDescriptors.indexOfFirst { it == capture.descriptor }
          if (index < 0) return
          latestCaptures.set(index, capture)
          captureSignal.tryEmit(Unit)
        }
      }
    }

    private fun accept(capture: RenderCapture) {
      tracker.onPaint(capture.iconRenderer)
      latestCapture.set(capture)
      captureSignal.tryEmit(Unit)
    }

    /**
     * SWING mode, plain components only (cell renderers paint all cells at the same place): a paint of a different icon into
     * the picked component at the same position and size as the last accepted paint is the same icon, recreated (e.g. a
     * toolbar button whose presentation produces a new SwingIcon on every update).
     */
    private fun isSuccessor(capture: RenderCapture): Boolean {
      if (picked.captureScope !== picked.component || capture.paintTarget !== picked.component) return false
      val last = latestCapture.get() ?: return false
      val a = last.paintInfo
      val b = capture.paintInfo
      return a.x == b.x && a.y == b.y && a.finalWidth == b.finalWidth && a.finalHeight == b.finalHeight
    }

    private fun currentLegacyCaptures(): List<RenderCapture> =
      (0 until latestCaptures.length()).mapNotNull { latestCaptures.get(it) }

    private fun processCapture() {
      if (closed) return
      if (mode == Mode.LEGACY) {
        val captures = currentLegacyCaptures()
        if (captures.isNotEmpty()) applyLegacy(captures)
      }
      else {
        latestCapture.get()?.let { apply(it, fromFallback = false) }
      }
    }

    /** Legacy root: the legacy structure with the captured nested SwingIcon paints attached to their BRIDGE nodes. */
    private fun applyLegacy(captures: List<RenderCapture>) {
      val legacyIcon = icon ?: return
      val key = captures.flatMap(::rendererIdentities)
      val latest = captures.maxBy { it.timestampNanos }
      val currentTarget = target
      if (captured && currentTarget != null && sameIdentities(key, structureKey)) {
        publish(currentTarget.copy(rendering = currentTarget.rendering.copy(lastPaint = RenderingSnapshots.lastPaint(latest))))
        return
      }
      captured = true
      structureKey = key
      val tree = InspectorTreeBuilder.buildForLegacy(idPrefix, legacyIcon, captures, descriptor, sizing)
      renderers = tree.renderers
      titles = collectTitles(tree.root)
      val summary = ArrayList(baseSummary())
      summary.add(InspectorProperty(DevkitIconsInspectorBundle.message("model.summary.mode"),
                                    DevkitIconsInspectorBundle.message("model.summary.mode.captured")))
      summary.add(InspectorProperty(DevkitIconsInspectorBundle.message("model.summary.note"),
                                    DevkitIconsInspectorBundle.message("model.summary.captured.nested")))
      publish(InspectionTarget(
        componentDescription = picked.description,
        iconClass = iconClassName(),
        bridged = bridged,
        summary = summary,
        root = tree.root,
        rendering = RenderingSnapshot(RenderingSnapshots.properties(legacyIcon, descriptor, latest), RenderingSnapshots.lastPaint(latest)),
        previews = previewsFor(descriptor),
        sources = sources,
      ))
      publishFlagged()
    }

    private fun onCaptureTimeout() {
      val fallback = fallbackCapture.get()
      if (fallback != null && latestCapture.compareAndSet(null, fallback)) {
        tracker.onPaint(fallback.iconRenderer)
        apply(fallback, fromFallback = true)
        return
      }
      val reason = when {
        mode == Mode.COMPOSE && compose?.capture == null -> DevkitIconsInspectorBundle.message("model.compose.static.not.icons.api")
        descriptorResult.isFailure -> DevkitIconsInspectorBundle.message("model.summary.static.no.descriptor",
                                                                         descriptorResult.exceptionOrNull().toString())
        bridged && capturedDescriptors.isEmpty() -> DevkitIconsInspectorBundle.message("model.summary.static.legacy", icon?.javaClass?.name)
        else -> DevkitIconsInspectorBundle.message("model.summary.static.no.paint", CAPTURE_TIMEOUT_MS)
      }
      publish(buildStaticTarget(reason))
    }

    private fun apply(capture: RenderCapture, fromFallback: Boolean) {
      val key = rendererIdentities(capture)
      val currentTarget = target
      if (edited != null && !applied && currentTarget != null) {
        // the edited copy is shown (not installed): live paints of the original only update the stability
        publish(currentTarget.copy(stability = tracker.snapshot()))
        return
      }
      if (captured && currentTarget != null && sameIdentities(key, structureKey)) {
        // same renderer instances: only the live paint rows and the stability change
        publish(currentTarget.copy(rendering = currentTarget.rendering.copy(lastPaint = RenderingSnapshots.lastPaint(capture)),
                                   stability = tracker.snapshot()))
        return
      }
      captured = true
      structureKey = key
      val tree = InspectorTreeBuilder.buildFromCapture(idPrefix, icon, capture, sizing)
      lastTree = tree
      editBase = capture.descriptor
      layerAddresses = tree.layerAddresses
      renderers = tree.renderers
      titles = collectTitles(tree.root)
      val root = withRecreations(tree.root, tracker.nodeRecreations(tree.renderers))
      val summary = ArrayList(baseSummary())
      summary.add(InspectorProperty(DevkitIconsInspectorBundle.message("model.summary.mode"),
                                    DevkitIconsInspectorBundle.message("model.summary.mode.captured")))
      if (mode == Mode.SWING && (fromFallback || capture.descriptor !== descriptor)) {
        summary.add(InspectorProperty(DevkitIconsInspectorBundle.message("model.summary.note"),
                                      DevkitIconsInspectorBundle.message("model.summary.captured.other.descriptor")))
      }
      publish(InspectionTarget(
        componentDescription = picked.description,
        iconClass = iconClassName(),
        bridged = bridged,
        summary = summary,
        root = root,
        rendering = snapshot(capture.descriptor, capture),
        previews = previewsFor(capture.descriptor),
        stability = tracker.snapshot(),
        sources = sources,
      ))
      publishFlagged()
    }

    /** Re-publishes the current target with fresh stability numbers (e.g. after [resetStability]). */
    fun republishStability() {
      val currentTarget = target ?: return
      if (currentTarget.stability == null) return
      val tree = lastTree
      val root = if (tree == null) currentTarget.root else withRecreations(tree.root, tracker.currentNodeCounts(tree.renderers.keys))
      publish(currentTarget.copy(root = root, stability = tracker.snapshot()))
    }

    /** True if the flag entry for [nodeId] (of this inspection) no longer flags the renderer currently at that node. */
    fun isReplaced(nodeId: String, flaggedRenderer: Any): Boolean {
      if (!nodeId.startsWith(idPrefix) || !captured) return false
      return renderers[nodeId] !== flaggedRenderer
    }

    private fun snapshot(painted: IconDescriptor?, capture: RenderCapture?): RenderingSnapshot {
      val base = RenderingSnapshots.snapshot(icon, painted, capture)
      if (mode != Mode.COMPOSE) return base
      return base.copy(properties = ComposeIcons.properties(compose, capture) + base.properties)
    }

    /** Previews are rendered once per inspection (a recreated icon at the same place looks the same). */
    private fun previewsFor(painted: IconDescriptor?): List<PreviewImage> {
      previews?.let { return it }
      return RenderingSnapshots.previews(painted).also { previews = it }
    }

    private fun iconClassName(): String = when {
      icon != null -> icon.javaClass.simpleName.ifEmpty { icon.javaClass.name }
      else -> "Compose icon"
    }

    private fun buildStaticTarget(reason: String): InspectionTarget {
      val tree = if (mode == Mode.COMPOSE && descriptor == null) InspectorTreeBuilder.buildComposeLegacy(idPrefix, compose!!)
      else InspectorTreeBuilder.buildStatic(idPrefix, icon, descriptor, compose, sizing)
      if (mode != Mode.LEGACY && descriptor != null) {
        editBase = descriptor
        layerAddresses = tree.layerAddresses
      }
      renderers = emptyMap()
      titles = collectTitles(tree.root)
      val summary = ArrayList(baseSummary())
      summary.add(InspectorProperty(DevkitIconsInspectorBundle.message("model.summary.mode"),
                                    DevkitIconsInspectorBundle.message("model.summary.mode.static")))
      summary.add(InspectorProperty(DevkitIconsInspectorBundle.message("model.summary.note"), reason))
      return InspectionTarget(
        componentDescription = picked.description,
        iconClass = iconClassName(),
        bridged = bridged,
        summary = summary,
        root = tree.root,
        rendering = snapshot(descriptor, null),
        previews = previewsFor(descriptor),
        sources = sources,
      )
    }

    private fun baseSummary(): List<InspectorProperty> {
      val rows = ArrayList(picked.summary)
      if (icon != null) {
        rows.add(InspectorProperty("icon class", icon.javaClass.name))
        rows.add(InspectorProperty("icon size", InspectorFormatting.iconSize(icon)))
      }
      rows.add(InspectorProperty("descriptor", descriptor?.let(::simpleClassName) ?: "unavailable"))
      if (bridged) rows.add(InspectorProperty("bridged", "converted from a legacy Swing icon via toIconDescriptor()"))
      return rows
    }

    private fun publish(newTarget: InspectionTarget) {
      if (closed || session !== this) return
      target = newTarget
      _state.value = InspectorState.Inspecting(decorated(newTarget))
      publishEditingState()
    }

    /** [target] with the "edited copy" summary row while edits exist. */
    private fun decorated(target: InspectionTarget): InspectionTarget {
      if (editCount == 0) return target
      val state = if (applied) DevkitIconsInspectorBundle.message("model.edit.state.applied")
      else DevkitIconsInspectorBundle.message("model.edit.state.preview")
      return target.copy(summary = listOf(InspectorProperty(DevkitIconsInspectorBundle.message("model.edit.summary", editCount), state)) +
                                   target.summary)
    }

    private fun publishEditingState() {
      if (closed || session !== this) return
      _modifierEditing.value = if (mode == Mode.LEGACY || editBase == null) null
      else ModifierEditingState(editCount, applied, canApplyToComponent(), editMessage)
    }

    // ---- modifier editing ----

    private fun canApplyToComponent(): Boolean {
      if (mode != Mode.SWING || picked.captureScope !== picked.component) return false
      val component = componentRef.get() ?: return false
      return component is ActionButton || component is JLabel || component is AbstractButton
    }

    /** Applies [edit] to the edited copy; returns an error message or null. */
    fun edit(edit: ModifierEdit): String? {
      val base = edited ?: editBase ?: return DevkitIconsInspectorBundle.message("model.edit.no.descriptor")
      val address = layerAddresses[edit.layerNodeId] ?: return DevkitIconsInspectorBundle.message("model.edit.not.editable")
      val result = ModifierEditing.apply(base, address, edit)
      val copy = result.getOrElse { return it.message ?: it.toString() }
      edited = copy
      editCount++
      editMessage = null
      if (applied) install()
      publishEdited()
      return null
    }

    private fun editedIcon(copy: IconDescriptor): SwingIcon = SwingIcon(copy, (icon as? SwingIcon)?.scale)

    /** Shows the edited copy (static tree, previews, sources); live paints take over once it is installed and painted. */
    private fun publishEdited() {
      val copy = edited ?: return
      val editedSwing = editedIcon(copy)
      val tree = InspectorTreeBuilder.buildStatic(idPrefix, if (mode == Mode.COMPOSE) null else editedSwing, copy, compose, sizing)
      editBase = copy
      layerAddresses = tree.layerAddresses
      renderers = emptyMap()
      titles = collectTitles(tree.root)
      captured = false
      structureKey = null
      val summary = ArrayList(baseSummary())
      summary.add(InspectorProperty(DevkitIconsInspectorBundle.message("model.summary.mode"),
                                    DevkitIconsInspectorBundle.message("model.summary.mode.static")))
      val editedPick = PickedIcon(picked.component, picked.captureScope, editedSwing, picked.description, picked.summary,
                                  picked.highlightBounds, picked.point)
      publish(InspectionTarget(
        componentDescription = picked.description,
        iconClass = editedSwing.javaClass.simpleName,
        bridged = false,
        summary = summary,
        root = tree.root,
        rendering = RenderingSnapshots.snapshot(editedSwing, copy, null),
        previews = RenderingSnapshots.previews(copy),
        stability = target?.stability,
        sources = runCatching { SvgSources.compute(editedPick, sizing) }.getOrDefault(emptyList()),
      ))
      publishFlagged()
    }

    fun setApplied(apply: Boolean) {
      if (apply == applied) return
      if (apply) {
        if (!canApplyToComponent()) {
          editMessage = DevkitIconsInspectorBundle.message("model.edit.cannot.apply")
          publishEditingState()
          return
        }
        if (edited == null) {
          editMessage = DevkitIconsInspectorBundle.message("model.edit.nothing.to.apply")
          publishEditingState()
          return
        }
        applied = true
        install()
        startReplacementWatch()
      }
      else {
        restoreComponent()
      }
      target?.let(::publish) ?: publishEditingState()
    }

    /** Installs the edited copy into the component (remembering the original icon to restore). */
    private fun install() {
      val component = componentRef.get() ?: return
      val copy = edited ?: return
      if (originalComponentIcon == null) originalComponentIcon = componentIcon(component)
      val editedSwing = editedIcon(copy)
      appliedDescriptor = copy
      installedIcon = editedSwing
      setComponentIcon(component, editedSwing)
      component.repaint()
    }

    /** Puts the original icon back if the edited copy is installed. */
    fun restoreComponent() {
      replacementTimer?.stop()
      replacementTimer = null
      if (!applied) return
      applied = false
      appliedDescriptor = null
      val component = componentRef.get()
      val original = originalComponentIcon
      if (component != null && original != null && componentIcon(component) === installedIcon) {
        setComponentIcon(component, original)
        component.repaint()
      }
      installedIcon = null
      originalComponentIcon = null
    }

    /**
     * Components may replace their icon on updates (e.g. `ActionButton.updateIcon` on a presentation refresh); while the
     * edited copy is applied, detect that and re-install it (throttled by the timer period).
     */
    private fun startReplacementWatch() {
      replacementTimer?.stop()
      replacementTimer = javax.swing.Timer(REPLACEMENT_CHECK_MS) {
        if (closed || !applied) return@Timer
        val component = componentRef.get() ?: return@Timer
        val current = componentIcon(component)
        if (current !== installedIcon) {
          replacements++
          originalComponentIcon = current
          install()
          editMessage = DevkitIconsInspectorBundle.message("model.edit.replaced", replacements)
          publishEditingState()
        }
      }.apply { start() }
    }

    private fun componentIcon(component: Component): Icon? = when (component) {
      // getIcon() may answer a hovered / disabled variant; the field is what setIcon() sets and updateIcon() replaces
      is ActionButton -> LegacyIcons.Reflect.get<Icon>(component, "myIcon") ?: component.icon
      is JLabel -> component.icon
      is AbstractButton -> component.icon
      else -> null
    }

    private fun setComponentIcon(component: Component, newIcon: Icon) {
      when (component) {
        is ActionButton -> component.setIcon(newIcon)
        is JLabel -> component.icon = newIcon
        is AbstractButton -> component.icon = newIcon
      }
    }

    fun close() {
      if (closed) return
      restoreComponent()
      closed = true
      try {
        captureHandle?.close()
      }
      catch (t: Throwable) {
        LOG.warn(t)
      }
      captureHandle = null
      jobs.forEach { it.cancel() }
      jobs.clear()
      renderers = emptyMap()
      lastTree = null
      for (i in 0 until latestCaptures.length()) latestCaptures.set(i, null)
      latestCapture.set(null)
      fallbackCapture.set(null)
    }
  }
}

private fun rendererIdentities(capture: RenderCapture): List<Any> {
  val result = ArrayList<Any>()
  fun visit(node: RenderNode, depth: Int) {
    if (depth > InspectorTreeBuilder.MAX_DEPTH) return
    result.add(node.renderer)
    node.subject?.let(result::add)
    node.children.forEach { visit(it, depth + 1) }
  }
  capture.roots.forEach { visit(it, 0) }
  return result
}

private fun sameIdentities(a: List<Any>, b: List<Any>?): Boolean {
  if (b == null || a.size != b.size) return false
  for (i in a.indices) {
    if (a[i] !== b[i]) return false
  }
  return true
}

private fun collectTitles(root: InspectorNode): Map<String, String> {
  val result = HashMap<String, String>()
  fun visit(node: InspectorNode) {
    result[node.id] = listOfNotNull(node.title, node.subtitle).joinToString(" · ")
    node.children.forEach(::visit)
  }
  visit(root)
  return result
}
