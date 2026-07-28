// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl

import com.intellij.diagnostic.PluginException
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.UI
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.LayoutManager2
import java.util.concurrent.atomic.AtomicLong
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

private val LOG = logger<EditorEmptyStateComponentController>()
internal const val EDITOR_ROOT_COMPONENT_CONSTRAINT: @NonNls String = "EditorRootComponent"
internal const val EMPTY_STATE_COMPONENT_CONSTRAINT: @NonNls String = "EditorEmptyStateComponent"
private val EMPTY_STATE_COMPONENT_CREATION_DELAY = 300.milliseconds

/**
 * Ceiling on how long a prepared component waits for the presentation gate to open.
 *
 * Not a latency knob — the gate is opened by project open as soon as it knows, and this is above any plausible project open. It is a
 * backstop against a hold nobody releases, which would otherwise leave this area showing nothing at all for as long as the project
 * stays open, because the fallback empty text is not selected while a rich provider is available.
 */
private val PRESENTATION_GATE_TIMEOUT = 30.seconds

/**
 * Budget for the part of a single [EditorEmptyStateComponentProvider.createComponent] call that does not run on the UI thread.
 *
 * A [EditorEmptyStateComponentProvider.Kind.FALLBACK] provider only builds Swing components, so it has nothing to spend this on.
 * A [EditorEmptyStateComponentProvider.Kind.RICH] one is allowed to resolve services and query a backend before it has anything to
 * build, so its budget is the point at which that stops being plausible.
 */
private fun slowPreparationThreshold(kind: EditorEmptyStateComponentProvider.Kind): Duration = when (kind) {
  EditorEmptyStateComponentProvider.Kind.FALLBACK -> 100.milliseconds
  EditorEmptyStateComponentProvider.Kind.RICH -> 1.seconds
}

/**
 * Budget for the UI-thread part of a single [EditorEmptyStateComponentProvider.createComponent] call, whatever the provider's kind.
 *
 * Preparation overlaps project open, so a UI-thread step this long is a startup freeze rather than a component that took its time —
 * which is why it is budgeted apart from [slowPreparationThreshold] instead of disappearing into a generous end-to-end number.
 * Only what a provider runs inside [buildEditorEmptyStateComponentOnUiThread] is measured against it.
 */
private val SLOW_UI_BUILD_THRESHOLD = 100.milliseconds

internal class EditorEmptyStateComponentController(
  private val splitters: EditorsSplitters,
  private val coroutineScope: CoroutineScope,
  private val showEmptyState: () -> Boolean,
) {
  private var componentHost: EditorEmptyStateComponentHost? = null
  private var componentEntries: List<EditorEmptyStateComponentEntry> = emptyList()
  private var creationJob: Job? = null
  private var creationGeneration: Int = 0
  private var richComponentsEnabled: Boolean = false

  /**
   * `false` while something else — startup opening editors of its own — still owns what this area is going to show.
   *
   * Components are prepared anyway, because preparation is invisible; only the mount waits for the gate, because a mount is not.
   */
  private val presentationAllowed = MutableStateFlow(true)
  private var creationDelay: Duration = EMPTY_STATE_COMPONENT_CREATION_DELAY
  private var presentationGateTimeout: Duration = PRESENTATION_GATE_TIMEOUT
  private var creationGate: (suspend () -> Unit)? = null

  init {
    coroutineScope.coroutineContext.job.invokeOnCompletion {
      disposeComponentsOnEdt()
    }
  }

  fun isCreationPending(): Boolean = creationJob != null

  fun isVisible(): Boolean = componentHost != null

  fun suppressRichComponents() {
    if (!richComponentsEnabled && componentHost == null && creationJob == null) {
      return
    }
    richComponentsEnabled = false
    disposeComponents()
  }

  fun enableRichComponents() {
    if (richComponentsEnabled) {
      return
    }
    richComponentsEnabled = true
    update()
  }

  /**
   * Opens or closes the presentation gate: while it is closed, components are still prepared, but nothing is mounted.
   *
   * Opening it is knowledge that nothing is going to take this area over any more, so a creation that was started under a closed
   * gate mounts without waiting out [EMPTY_STATE_COMPONENT_CREATION_DELAY] — the delay is only a guess that an editor may still be
   * arriving, and where there is knowledge there is no need to also guess.
   */
  fun setPresentationAllowed(allowed: Boolean) {
    if (presentationAllowed.value == allowed) {
      return
    }
    presentationAllowed.value = allowed
    if (allowed) {
      // a creation parked on the gate mounts on its own; this covers the case where there is nothing parked yet
      update()
    }
  }

  fun update() {
    if (showEmptyState()) {
      showComponents()
    }
    else {
      disposeComponents()
    }
  }

  fun rebuild() {
    disposeComponents()
    update()
  }

  fun cancelCreation() {
    val job = creationJob ?: return
    // bump the generation before clearing the job, so the cancelled job's `finally` cannot clear a job started after it
    creationGeneration++
    creationJob = null
    job.cancel()
  }

  fun disposeComponents() {
    cancelCreation()
    val host = componentHost ?: return
    // uninstalling fires `removeNotify` on a provider's component, which may release an editor — see [mount]
    WriteIntentReadAction.run {
      splitters.uninstallEmptyStateOverlay(host)
      host.removeAll()
      disposeEntries(componentEntries)
    }
    componentHost = null
    componentEntries = emptyList()
    splitters.revalidate()
    splitters.repaint()
  }

  private fun disposeComponentsOnEdt() {
    val application = ApplicationManager.getApplication()
    // the scope may be cancelled from a strict-UI context, where taking the write-intent lock disposal needs is forbidden rather than
    // merely absent — so the direct path is taken only where the lock is already held
    if (application.isDispatchThread && application.isWriteIntentLockAcquired) {
      disposeComponents()
    }
    else {
      application.invokeLater({ disposeComponents() }, ModalityState.any())
    }
  }

  /** @param delay `null` restores the production delay. */
  fun setCreationDelayForTests(delay: Duration?) {
    creationDelay = delay ?: EMPTY_STATE_COMPONENT_CREATION_DELAY
  }

  /** @param timeout `null` restores the production timeout. */
  fun setPresentationGateTimeoutForTests(timeout: Duration?) {
    presentationGateTimeout = timeout ?: PRESENTATION_GATE_TIMEOUT
  }

  fun setCreationGateForTests(gate: (suspend () -> Unit)?) {
    creationGate = gate
  }

  private fun showComponents() {
    if (componentHost != null || creationJob != null) {
      return
    }
    val providers = getProvidersToCreate()
    if (providers.isEmpty()) {
      return
    }
    val kind = providers.first().kind
    if (kind == EditorEmptyStateComponentProvider.Kind.RICH && !richComponentsEnabled) {
      return
    }

    // components are built first and presented afterwards, so a closed gate costs no latency: by the time it opens they are ready
    val presentationHeld = !presentationAllowed.value
    val generation = ++creationGeneration
    creationJob = coroutineScope.launch(Dispatchers.Default + CoroutineName("create editor empty state components")) {
      val startedAt = TimeSource.Monotonic.markNow()
      var entries: List<EditorEmptyStateComponentEntry> = emptyList()
      var mounted = false
      try {
        creationGate?.invoke()
        if (!isCreationValidOnUiThread(generation, kind)) {
          return@launch
        }
        entries = createEntries(generation, providers)
        if (entries.isEmpty() && kind == EditorEmptyStateComponentProvider.Kind.RICH) {
          val fallbackProviders = withContext(Dispatchers.UI) {
            if (isCreationValid(generation, EditorEmptyStateComponentProvider.Kind.FALLBACK)) {
              getAvailableProviders(EditorEmptyStateComponentProvider.Kind.FALLBACK)
            }
            else {
              emptyList()
            }
          }
          entries = createEntries(generation, fallbackProviders)
        }
        if (entries.isEmpty()) {
          return@launch
        }
        // Only a rich empty state is worth delaying or holding back — the plain empty text is what this area showed before any of this
        // existed. Keyed on what was actually built, so a fallback reached through a rich provider that built nothing is not delayed.
        val presentedKind = entries.first().kind
        if (presentedKind == EditorEmptyStateComponentProvider.Kind.RICH) {
          if (!presentationHeld) {
            delay(creationDelay)
          }
          // the gate may also have been closed after this creation started, so it is awaited whether it was held at that point or not
          awaitPresentationAllowed()
        }
        // `Dispatchers.EDT`, not `Dispatchers.UI`: mounting takes the write-intent lock (see [mount]), and the strict UI dispatcher
        // forbids taking it outright. `ModalityState.any()`, like the hold hops in `IdeProjectFrameAllocator`, so a modal dialog
        // during startup cannot reorder the mount against the release that allowed it.
        withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
          if (!isCreationValid(generation, presentedKind)) {
            return@withContext
          }
          mount(entries)
          mounted = true
        }
      }
      finally {
        withContext(NonCancellable + Dispatchers.EDT + ModalityState.any().asContextElement()) {
          if (!mounted) {
            if (entries.isNotEmpty()) {
              // what the split costs when project open takes this area over anyway: components were built and are now thrown away
              LOG.debug { "Discarded ${entries.size} prepared editor empty state component(s) after ${startedAt.elapsedNow()}" }
            }
            disposeEntries(entries)
          }
          if (generation == creationGeneration) {
            creationJob = null
            if (!mounted && richComponentsEnabled && showEmptyState()) {
              splitters.repaint()
            }
          }
        }
      }
    }
  }

  private suspend fun awaitPresentationAllowed() {
    if (withTimeoutOrNull(presentationGateTimeout) { presentationAllowed.first { it } } == null) {
      LOG.warn("Editor empty state presentation was held for $presentationGateTimeout and is presented anyway; a hold was never released")
    }
  }

  private fun getProvidersToCreate(): List<EditorEmptyStateProviderEntry> {
    val richProviders = getAvailableProviders(EditorEmptyStateComponentProvider.Kind.RICH)
    if (richProviders.isNotEmpty()) {
      return richProviders
    }
    return getAvailableProviders(EditorEmptyStateComponentProvider.Kind.FALLBACK)
  }

  private fun getAvailableProviders(kind: EditorEmptyStateComponentProvider.Kind): List<EditorEmptyStateProviderEntry> {
    val providers = ArrayList<EditorEmptyStateProviderEntry>()
    EditorEmptyStateComponentProvider.EP_NAME.processWithPluginDescriptor { provider, pluginDescriptor ->
      if (getProviderKind(provider, pluginDescriptor) != kind) {
        return@processWithPluginDescriptor
      }
      if (isProviderAvailable(provider, pluginDescriptor)) {
        providers.add(EditorEmptyStateProviderEntry(provider, pluginDescriptor, kind))
      }
    }
    return providers
  }

  private fun getProviderKind(
    provider: EditorEmptyStateComponentProvider,
    pluginDescriptor: PluginDescriptor,
  ): EditorEmptyStateComponentProvider.Kind? {
    return try {
      provider.getKind()
    }
    catch (e: Throwable) {
      LOG.error(PluginException("Cannot get editor empty state component kind using $provider", e, pluginDescriptor.pluginId))
      null
    }
  }

  private fun isProviderAvailable(
    provider: EditorEmptyStateComponentProvider,
    pluginDescriptor: PluginDescriptor,
  ): Boolean {
    return try {
      provider.isAvailable(splitters)
    }
    catch (e: Throwable) {
      LOG.error(PluginException("Cannot check editor empty state component availability using $provider", e, pluginDescriptor.pluginId))
      false
    }
  }

  private suspend fun isCreationValidOnUiThread(
    generation: Int,
    kind: EditorEmptyStateComponentProvider.Kind,
  ): Boolean = withContext(Dispatchers.UI) {
    isCreationValid(generation, kind)
  }

  private fun isCreationValid(generation: Int, kind: EditorEmptyStateComponentProvider.Kind): Boolean {
    return generation == creationGeneration &&
           (kind == EditorEmptyStateComponentProvider.Kind.FALLBACK || richComponentsEnabled) &&
           showEmptyState() &&
           componentHost == null
  }

  private suspend fun createEntries(
    generation: Int,
    providers: List<EditorEmptyStateProviderEntry>,
  ): List<EditorEmptyStateComponentEntry> {
    val entries = ArrayList<EditorEmptyStateComponentEntry>()
    try {
      for ((provider, pluginDescriptor, kind) in providers) {
        if (!isCreationValidOnUiThread(generation, kind)) {
          break
        }
        val component = try {
          val uiBuildTime = EditorEmptyStateUiBuildTime()
          val startedAt = TimeSource.Monotonic.markNow()
          val result = withContext(uiBuildTime) { provider.createComponent(splitters) }
          reportSlowPreparation(
            provider = provider,
            pluginDescriptor = pluginDescriptor,
            kind = kind,
            elapsed = startedAt.elapsedNow(),
            uiElapsed = uiBuildTime.elapsed,
          )
          result
        }
        catch (e: CancellationException) {
          throw e
        }
        catch (e: Throwable) {
          LOG.error(PluginException("Cannot create editor empty state component using $provider", e, pluginDescriptor.pluginId))
          null
        }
        if (component != null) {
          entries.add(EditorEmptyStateComponentEntry(provider, component, kind))
        }
      }
      return entries
    }
    catch (e: CancellationException) {
      // `Dispatchers.EDT` for the same reason as the mount: disposing takes the write-intent lock
      withContext(NonCancellable + Dispatchers.EDT + ModalityState.any().asContextElement()) {
        disposeEntries(entries)
      }
      throw e
    }
  }

  /**
   * Mounts the prepared components. Must be called where the write-intent lock may be taken — the legacy [Dispatchers.EDT], or
   * `invokeLater` — because [Dispatchers.UI] forbids taking it rather than merely not carrying it.
   */
  private fun mount(entries: List<EditorEmptyStateComponentEntry>) {
    // Installing the overlay is a plain `Container.add`, but it fires `addNotify` on a provider's component, and a provider may create
    // an editor there — AIR's composer hosts an `AirPromptEditorTextField`, whose `addNotify` runs `EditorTextField.initEditor`. The
    // lock is taken here, where the need is, so that it is stated rather than inherited from whichever caller arrives.
    WriteIntentReadAction.run {
      val host = EditorEmptyStateComponentHost(fillContent = entries.all { it.kind == EditorEmptyStateComponentProvider.Kind.FALLBACK })
      componentHost = host
      componentEntries = entries
      host.setComponents(entries.map { it.component })
      splitters.installEmptyStateOverlay(host)
      splitters.revalidate()
      splitters.repaint()
    }
  }

  /**
   * Takes the write-intent lock for the same reason [mount] does: a provider may release an editor while disposing its component. Same
   * caller requirement, too — a context where that lock may be taken.
   */
  private fun disposeEntries(entries: List<EditorEmptyStateComponentEntry>) {
    if (entries.isEmpty()) {
      return
    }
    WriteIntentReadAction.run {
      for ((provider, component) in entries) {
        provider.disposeComponent(component)
      }
    }
  }
}

private fun reportSlowPreparation(
  provider: EditorEmptyStateComponentProvider,
  pluginDescriptor: PluginDescriptor,
  kind: EditorEmptyStateComponentProvider.Kind,
  elapsed: Duration,
  uiElapsed: Duration,
) {
  val offUiElapsed = maxOf(Duration.ZERO, elapsed - uiElapsed)
  if (uiElapsed < SLOW_UI_BUILD_THRESHOLD && offUiElapsed < slowPreparationThreshold(kind)) {
    return
  }
  LOG.warn(
    "Slow editor empty state component preparation by $provider from ${pluginDescriptor.pluginId}: " +
    "$elapsed, of which $uiElapsed on the UI thread"
  )
}

/**
 * How long a provider spent building on the UI thread, accumulated by [buildEditorEmptyStateComponentOnUiThread].
 *
 * A context element rather than a return value, so that a provider reports it by choosing where to hop rather than by threading a
 * measurement back through its own signature.
 */
internal class EditorEmptyStateUiBuildTime : AbstractCoroutineContextElement(Key) {
  companion object Key : CoroutineContext.Key<EditorEmptyStateUiBuildTime>

  private val nanos = AtomicLong()

  fun add(duration: Duration) {
    nanos.addAndGet(duration.inWholeNanoseconds)
  }

  val elapsed: Duration
    get() = nanos.get().nanoseconds
}

private data class EditorEmptyStateProviderEntry(
  val provider: EditorEmptyStateComponentProvider,
  val pluginDescriptor: PluginDescriptor,
  val kind: EditorEmptyStateComponentProvider.Kind,
)

private data class EditorEmptyStateComponentEntry(
  val provider: EditorEmptyStateComponentProvider,
  val component: JComponent,
  val kind: EditorEmptyStateComponentProvider.Kind,
)

internal class EditorsSplittersLayout : LayoutManager2 {
  private var editorRoot: JComponent? = null
  internal var emptyStateOverlay: Component? = null
    private set

  internal val editorRootComponent: JComponent?
    get() = editorRoot

  override fun addLayoutComponent(comp: Component, constraints: Any?) {
    when (constraints) {
      null, BorderLayout.CENTER, EDITOR_ROOT_COMPONENT_CONSTRAINT -> setEditorRoot(comp)
      EMPTY_STATE_COMPONENT_CONSTRAINT -> emptyStateOverlay = comp
      else -> throw IllegalArgumentException("Unsupported EditorsSplitters layout constraint: $constraints")
    }
  }

  override fun removeLayoutComponent(comp: Component) {
    if (editorRoot === comp) {
      editorRoot = null
    }
    if (emptyStateOverlay === comp) {
      emptyStateOverlay = null
    }
  }

  override fun layoutContainer(target: Container) {
    val insets = target.insets
    val x = insets.left
    val y = insets.top
    val width = maxOf(0, target.width - insets.left - insets.right)
    val height = maxOf(0, target.height - insets.top - insets.bottom)
    editorRoot?.setBounds(x, y, width, height)
    emptyStateOverlay?.setBounds(x, y, width, height)
  }

  override fun preferredLayoutSize(parent: Container): Dimension = editorRoot?.preferredSize.withInsets(parent)

  override fun minimumLayoutSize(parent: Container): Dimension = editorRoot?.minimumSize.withInsets(parent)

  override fun maximumLayoutSize(target: Container): Dimension = Dimension(Int.MAX_VALUE, Int.MAX_VALUE)

  override fun getLayoutAlignmentX(target: Container): Float = 0.5f

  override fun getLayoutAlignmentY(target: Container): Float = 0.5f

  override fun invalidateLayout(target: Container) {
  }

  override fun addLayoutComponent(name: String?, comp: Component) {
    addLayoutComponent(comp, name)
  }

  private fun setEditorRoot(comp: Component) {
    require(comp is JComponent) { "EditorsSplitters editor root must be a JComponent: ${comp.javaClass.name}" }
    editorRoot = comp
  }

  private fun Dimension?.withInsets(parent: Container): Dimension {
    val insets = parent.insets
    val width = (this?.width ?: 0) + insets.left + insets.right
    val height = (this?.height ?: 0) + insets.top + insets.bottom
    return Dimension(width, height)
  }
}

@ApiStatus.Internal
class EditorEmptyStateComponentHost(private val fillContent: Boolean) : JPanel() {
  private val contentPanel = JPanel().apply {
    isOpaque = false
    layout = BoxLayout(this, BoxLayout.Y_AXIS)
  }

  init {
    isOpaque = false
    if (fillContent) {
      layout = BorderLayout()
    }
    else {
      layout = GridBagLayout()
      add(contentPanel, GridBagConstraints().apply {
        gridx = 0
        gridy = 0
        weightx = 1.0
        weighty = 1.0
        anchor = GridBagConstraints.CENTER
        insets = JBUI.insets(24)
      })
    }
  }

  fun setComponents(components: List<JComponent>) {
    if (fillContent) {
      removeAll()
      components.singleOrNull()?.let { add(it, BorderLayout.CENTER) }
      return
    }

    contentPanel.removeAll()
    for ((index, component) in components.withIndex()) {
      component.alignmentX = CENTER_ALIGNMENT
      contentPanel.add(component)
      if (index < components.lastIndex) {
        contentPanel.add(Box.createVerticalStrut(JBUI.scale(8)))
      }
    }
  }

  override fun getPreferredSize(): Dimension = Dimension(0, 0)
}
