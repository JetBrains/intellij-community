// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.pinned.items

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.Alarm
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.impl.PinToTopManagerState
import com.intellij.xdebugger.impl.XDebuggerManagerImpl
import com.intellij.xdebugger.impl.ui.tree.nodes.XDebuggerTreeNode
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueContainerNode
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl
import icons.PlatformDebuggerImplIcons
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus.Internal
import java.lang.ref.WeakReference

@Internal
class XDebuggerPinToTopManager(coroutineScope: CoroutineScope) {
  companion object {
    fun getInstance(project: Project): XDebuggerPinToTopManager {
      return (XDebuggerManager.getInstance(project) as XDebuggerManagerImpl).pinToTopManager
    }

    private const val DEFAULT_ICON_DELAY = 300L
  }

  private val listeners = mutableListOf<XDebuggerPinToTopListener>()

  private var nodeHoverLifetime: Disposable? = null
  private var activeNode: WeakReference<XValueNodeImpl>? = null
  private var pinnedMembers = HashSet<PinnedItemInfo>()
  private val pinToTopIconAlarm = Alarm(threadToUse = Alarm.ThreadToUse.SWING_THREAD, coroutineScope = coroutineScope)

  val pinToTopComparator: Comparator<XValueNodeImpl> = Comparator.comparing { !isItemPinned(it) }
  val compoundComparator = pinToTopComparator.then(XValueNodeImpl.COMPARATOR)

  fun isEnabled(): Boolean = Registry.`is`("debugger.field.pin.to.top", true)

  fun onNodeHovered(node: XDebuggerTreeNode?, lifetimeHolder: Disposable) {
    if (activeNode?.get() == node) {
      return
    }
    if (!isEnabled()) {
      return
    }

    disposeCurrentNodeHoverSubscription()

    if (!isPinToTopSupported(node)) {
      return
    }

    val valueNode = node as? XValueNodeImpl ?: return
    if (!valueNode.canBePinned() || node.isPinned(this)) {
      return
    }

    var oldIcon = valueNode.icon
    val nodeRef = WeakReference(node)

    val changeIconLifetime = Disposable {
      val node = nodeRef.get() ?: return@Disposable
      val xValuePresentation = node.valuePresentation
      if (node.icon == PlatformDebuggerImplIcons.PinToTop.UnpinnedItem && xValuePresentation != null) {
        node.setPresentation(oldIcon, xValuePresentation, !node.isLeaf)
      }
      activeNode = null
      nodeHoverLifetime = null
    }
    activeNode = nodeRef
    nodeHoverLifetime = changeIconLifetime

    pinToTopIconAlarm.addRequest(
      request = {
        val xValuePresentation = node.valuePresentation ?: return@addRequest
        // update icon with actual value
        oldIcon = node.icon
        node.setPresentation(PlatformDebuggerImplIcons.PinToTop.UnpinnedItem, xValuePresentation, !node.isLeaf)
      },
      delayMillis = DEFAULT_ICON_DELAY,
    )
    Disposer.register(lifetimeHolder, changeIconLifetime)
  }

  fun addListener(listener: XDebuggerPinToTopListener, disposable: Disposable) {
    listeners.add(listener)
    Disposer.register(disposable, Disposable { listeners.remove(listener) })
  }

  fun removeListener(listener: XDebuggerPinToTopListener) {
    listeners.remove(listener)
  }

  fun getPinnedItemInfos() = pinnedMembers.toList()

  fun addItemInfo(info: PinnedItemInfo) {
    pinnedMembers.add(info)
    for (listener in listeners) {
      listener.onPinnedItemAdded(info)
    }
  }

  fun removeItemInfo(info: PinnedItemInfo) {
    pinnedMembers.remove(info)
    for (listener in listeners) {
      listener.onPinnedItemRemoved(info)
    }
  }

  fun isItemPinned(node: XValueNodeImpl?): Boolean = node.isPinned(this)

  fun isPinned(pinnedItemInfo: PinnedItemInfo): Boolean = pinnedMembers.contains(pinnedItemInfo)

  private fun disposeCurrentNodeHoverSubscription() {
    Disposer.dispose(nodeHoverLifetime ?: return)
    nodeHoverLifetime = null
    pinToTopIconAlarm.cancelAllRequests()
  }

  fun saveState(state: PinToTopManagerState) {
    state.pinnedMembersList = pinnedMembers.toMutableList()
  }

  fun loadState(state: PinToTopManagerState) {
    pinnedMembers.addAll(state.pinnedMembersList)
  }

  fun isPinToTopSupported(node: XDebuggerTreeNode?): Boolean {
    return if (node is XValueContainerNode<*>) return node.valueContainer is PinToTopValue else false
  }
}

