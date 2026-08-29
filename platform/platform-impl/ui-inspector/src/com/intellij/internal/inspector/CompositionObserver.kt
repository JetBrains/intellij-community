// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.inspector

import androidx.compose.runtime.ExperimentalComposeRuntimeApi
import androidx.compose.runtime.RecomposeScope
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.tooling.ComposeToolingApi
import androidx.compose.runtime.tooling.CompositionData
import androidx.compose.runtime.tooling.CompositionObserver
import androidx.compose.runtime.tooling.CompositionObserverHandle
import androidx.compose.runtime.tooling.CompositionRegistrationObserver
import androidx.compose.runtime.tooling.IdentifiableRecomposeScope
import androidx.compose.runtime.tooling.ObservableComposition
import androidx.compose.runtime.tooling.observe
import com.intellij.openapi.Disposable
import com.intellij.util.ui.EDT
import org.jetbrains.compose.swing.core.findRecomposer
import org.jetbrains.compose.swing.node.SwingComponentNode
import org.jetbrains.compose.swing.tooling.findCompositionData
import java.awt.Component
import java.awt.Container
import java.util.WeakHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Counts `startRestartGroup` calls for the nearest declaring scope, as the Android Layout Inspector does.
 * Components from the same scope share a count. [ComposeUiInspector.startRecording] supplies ownership data.
 * [dispose] stops observation and clears all counts.
 */
@OptIn(ExperimentalComposeRuntimeApi::class, ComposeToolingApi::class)
internal class CompositionObserver : Disposable {
  private class ScopeCounts {
    var recompositions: Int = 0
  }

  private object NoAnchor

  private object NotCompose

  private val lock = Any()

  // An anchor survives replacement of its RecomposeScope object.
  private val counts = HashMap<Any, ScopeCounts>()

  // Cache one slot-table walk per component. Each pass clears it, and weak keys release removed components.
  private val anchors = WeakHashMap<Component, Any>()
  private val observedRecomposers = HashMap<Recomposer, CompositionObserverHandle>()
  private val observedCompositions = HashMap<ObservableComposition, CompositionObserverHandle>()
  private val compositionListeners = CopyOnWriteArrayList<Runnable>()

  /** Calls [listener] on the EDT after each observed composition pass. */
  fun addCompositionListener(listener: Runnable) {
    compositionListeners.add(listener)
  }

  fun removeCompositionListener(listener: Runnable) {
    compositionListeners.remove(listener)
  }

  /** Observes [root] on the EDT before the recording-triggered pass can run. */
  fun observeAll(root: Component) {
    if (!EDT.isCurrentThreadEdt()) return
    composeOrNull { observeBelow(root) }
  }

  private fun observeBelow(component: Component) {
    observe(component)
    if (component is Container) component.components.forEach { observeBelow(it) }
  }

  /**
   * Returns the declaring scope count, or `null` for an unrecorded or non-Compose component.
   * A recorded component returns zero before its scope recomposes. Call on the EDT.
   */
  fun recompositionsOf(component: Component): Int? {
    if (!EDT.isCurrentThreadEdt() || !ComposeUiInspector.isRecording) return null
    return composeOrNull { readRecompositionsOf(component) }
  }

  /** Stops observation and clears all counts. Call on the EDT. */
  override fun dispose() {
    synchronized(lock) {
      observedCompositions.values.forEach { it.dispose() }
      observedCompositions.clear()
      observedRecomposers.values.forEach { it.dispose() }
      observedRecomposers.clear()
      counts.clear()
      anchors.clear()
    }
    compositionListeners.clear()
  }

  private fun readRecompositionsOf(component: Component): Int? {
    observe(component)
    return when (val answer = synchronized(lock) { anchors[component] } ?: findAnchorOf(component)) {
      NotCompose -> null
      NoAnchor -> 0
      else -> recompositionsOfAnchor(answer)
    }
  }

  private fun findAnchorOf(component: Component): Any {
    val answer = when {
      !ComposeUiInspector.isComposeComponent(component) -> NotCompose
      // The declaring group can have no enclosing scope.
      else -> component.declaringAnchor() ?: NoAnchor
    }
    synchronized(lock) { anchors[component] = answer }
    return answer
  }

  private fun recompositionsOfAnchor(anchor: Any): Int = synchronized(lock) { counts[anchor]?.recompositions } ?: 0

  /** Observes the component's recomposer, including later compositions and content without a window. */
  private fun observe(component: Component) {
    val recomposer = component.findRecomposer() ?: return
    synchronized(lock) {
      if (recomposer !in observedRecomposers) {
        observedRecomposers[recomposer] = recomposer.observe(Registrations())
      }
    }
  }

  private inner class Registrations : CompositionRegistrationObserver {
    override fun onCompositionRegistered(composition: ObservableComposition) {
      synchronized(lock) {
        // A child composition reports through its own observer, so each pass is counted once.
        observedCompositions[composition] = composition.setObserver(Counting())
      }
    }

    override fun onCompositionUnregistered(composition: ObservableComposition) {
      synchronized(lock) { observedCompositions.remove(composition)?.dispose() }
    }
  }

  private inner class Counting : CompositionObserver {
    override fun onBeginComposition(composition: ObservableComposition) {}

    /** Counts `startRestartGroup` calls, as the Android Layout Inspector does. */
    override fun onScopeEnter(scope: RecomposeScope) {
      val anchor = scope.anchor ?: return
      synchronized(lock) { counts.getOrPut(anchor) { ScopeCounts() }.recompositions++ }
    }

    override fun onReadInScope(scope: RecomposeScope, value: Any) {}

    override fun onScopeExit(scope: RecomposeScope) {}

    override fun onEndComposition(composition: ObservableComposition) {
      // Recomposition rewrites the slot table, so invalidate component anchors.
      synchronized(lock) { anchors.clear() }
      compositionListeners.forEach { it.run() }
    }

    override fun onScopeInvalidated(scope: RecomposeScope, value: Any?) {}

    override fun onScopeDisposed(scope: RecomposeScope) {
      val anchor = scope.anchor ?: return
      synchronized(lock) { counts.remove(anchor) }
    }
  }

  private val RecomposeScope.anchor: Any?
    get() = (this as? IdentifiableRecomposeScope)?.identity

  /**
   * Finds the nearest declaring scope.
   * A component can host another composition, so search ancestors for the first slot table that declares it.
   */
  private fun Component.declaringAnchor(): Any? =
    generateSequence(this) { it.parent }
      .mapNotNull { it.findCompositionData() }
      .distinct()
      .firstNotNullOfOrNull { it.enclosingAnchorOf(this, null) }

  /** Carries parent scopes down because the compiler stores scopes above declared nodes. */
  private fun CompositionData.enclosingAnchorOf(component: Component, enclosing: Any?): Any? {
    for (group in compositionGroups) {
      val anchor = group.data.firstNotNullOfOrNull { (it as? RecomposeScope)?.anchor } ?: enclosing
      if ((group.node as? SwingComponentNode)?.component === component) return anchor
      group.enclosingAnchorOf(component, anchor)?.let { return it }
    }
    return null
  }
}
