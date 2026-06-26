// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl

import com.intellij.diagnostic.PluginException
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeIntentReadAction
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.NonNls
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.LayoutManager2
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

private val LOG = logger<EditorEmptyStateComponentController>()
internal const val EDITOR_ROOT_COMPONENT_CONSTRAINT: @NonNls String = "EditorRootComponent"
internal const val EMPTY_STATE_COMPONENT_CONSTRAINT: @NonNls String = "EditorEmptyStateComponent"
private val EMPTY_STATE_COMPONENT_CREATION_DELAY = 300.milliseconds
private val SLOW_EMPTY_STATE_COMPONENT_PROVIDER_THRESHOLD = 100.milliseconds

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
  private var creationDelay: Duration = EMPTY_STATE_COMPONENT_CREATION_DELAY
  private var creationGate: (suspend () -> Unit)? = null

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
    creationGeneration++
    creationJob = null
    job.cancel()
  }

  fun disposeComponents() {
    cancelCreation()
    val host = componentHost ?: return
    splitters.uninstallEmptyStateOverlay(host)
    host.removeAll()
    disposeEntries(componentEntries)
    componentHost = null
    componentEntries = emptyList()
    splitters.revalidate()
    splitters.repaint()
  }

  fun setCreationDelayForTests(delay: Duration) {
    creationDelay = delay
  }

  fun setCreationGateForTests(gate: (suspend () -> Unit)?) {
    creationGate = gate
  }

  private fun showComponents() {
    if (!richComponentsEnabled || componentHost != null || creationJob != null) {
      return
    }

    val generation = ++creationGeneration
    creationJob = coroutineScope.launch(Dispatchers.Default + CoroutineName("create editor empty state components")) {
      var entries: List<EditorEmptyStateComponentEntry> = emptyList()
      var mounted = false
      try {
        delay(creationDelay)
        creationGate?.invoke()
        if (!isCreationValidOnEdt(generation)) {
          return@launch
        }
        entries = createEntries(generation)
        withContext(Dispatchers.EDT) {
          if (!isCreationValid(generation)) {
            return@withContext
          }
          if (entries.isEmpty()) {
            return@withContext
          }
          writeIntentReadAction {
            mount(entries)
          }
          mounted = true
        }
      }
      finally {
        withContext(NonCancellable + Dispatchers.EDT) {
          if (!mounted) {
            disposeEntries(entries)
          }
          if (generation == creationGeneration) {
            creationJob = null
          }
        }
      }
    }
  }

  private suspend fun isCreationValidOnEdt(generation: Int): Boolean = withContext(Dispatchers.EDT) {
    isCreationValid(generation)
  }

  private fun isCreationValid(generation: Int): Boolean {
    return generation == creationGeneration &&
           richComponentsEnabled &&
           showEmptyState() &&
           componentHost == null
  }

  private suspend fun createEntries(generation: Int): List<EditorEmptyStateComponentEntry> {
    val providers = ArrayList<Pair<EditorEmptyStateComponentProvider, PluginDescriptor>>()
    EditorEmptyStateComponentProvider.EP_NAME.processWithPluginDescriptor { provider, pluginDescriptor ->
      providers.add(provider to pluginDescriptor)
    }

    val entries = ArrayList<EditorEmptyStateComponentEntry>()
    try {
      for ((provider, pluginDescriptor) in providers) {
        if (!isCreationValidOnEdt(generation)) {
          break
        }
        val component = try {
          val startedAt = TimeSource.Monotonic.markNow()
          val result = provider.createComponent(splitters)
          val elapsed = startedAt.elapsedNow()
          if (elapsed >= SLOW_EMPTY_STATE_COMPONENT_PROVIDER_THRESHOLD) {
            LOG.warn("Slow editor empty state component provider $provider from ${pluginDescriptor.pluginId}: $elapsed")
          }
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
          entries.add(EditorEmptyStateComponentEntry(provider, component))
        }
      }
      return entries
    }
    catch (e: CancellationException) {
      withContext(NonCancellable + Dispatchers.EDT) {
        disposeEntries(entries)
      }
      throw e
    }
  }

  private fun mount(entries: List<EditorEmptyStateComponentEntry>) {
    val host = EditorEmptyStateComponentHost()
    componentHost = host
    componentEntries = entries
    host.setComponents(entries.map { it.component })
    splitters.installEmptyStateOverlay(host)
    splitters.revalidate()
    splitters.repaint()
  }

  private fun disposeEntries(entries: List<EditorEmptyStateComponentEntry>) {
    for ((provider, component) in entries) {
      provider.disposeComponent(component)
    }
  }
}

private data class EditorEmptyStateComponentEntry(
  val provider: EditorEmptyStateComponentProvider,
  val component: JComponent,
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

private class EditorEmptyStateComponentHost : JPanel(GridBagLayout()) {
  private val contentPanel = JPanel().apply {
    isOpaque = false
    layout = BoxLayout(this, BoxLayout.Y_AXIS)
  }

  init {
    isOpaque = false
    add(contentPanel, GridBagConstraints().apply {
      gridx = 0
      gridy = 0
      weightx = 1.0
      weighty = 1.0
      anchor = GridBagConstraints.CENTER
      insets = JBUI.insets(24)
    })
  }

  fun setComponents(components: List<JComponent>) {
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
