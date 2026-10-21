// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.headertoolbar

import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.ActionToolbarListener
import com.intellij.openapi.actionSystem.CustomShortcutSet
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.impl.ActionMenu
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.impl.ToolbarComboButton
import com.intellij.openapi.wm.impl.ToolbarSplitButton
import com.intellij.openapi.wm.impl.customFrameDecorations.header.toolbar.ToolbarFrameHeader
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.table.ComponentsListFocusTraversalPolicy
import java.awt.AWTKeyStroke
import java.awt.Component
import java.awt.Container
import java.awt.FocusTraversalPolicy
import java.awt.KeyboardFocusManager
import java.awt.event.ContainerAdapter
import java.awt.event.ContainerEvent
import java.awt.event.KeyEvent
import java.lang.ref.WeakReference
import javax.swing.AbstractButton
import javax.swing.JComponent
import javax.swing.KeyStroke
import javax.swing.SwingUtilities

private const val MAIN_TOOLBAR_FOCUS_REGISTRATION = "MainToolbar.focusRegistration"

internal class MainToolbarFocusSupport(
  private val toolbar: JComponent,
  private val parentDisposable: Disposable,
) {
  private var focusFallbackScheduled = false
  private var pendingFocusFallbackIndex: Int? = null
  private var restoreFocusTargetRef: WeakReference<Component>? = null

  fun install() {
    installHeaderToolbarFocusTraversalPolicy(toolbar, MainToolbarFocusTraversalPolicy())
    DumbAwareAction.create { restoreFocusToPreviousComponent() }
      .registerCustomShortcutSet(KeyEvent.VK_ESCAPE, 0, toolbar)
  }

  fun focusFirstItem() {
    cancelFocusFallback()
    val component = getFocusableAndEnabledItems().firstOrNull() ?: return
    val focusOwner = currentFocusOwner()
    val isFocusOutsideToolbar = focusOwner != null && !SwingUtilities.isDescendingFrom(focusOwner, getToolbarFrameHeader() ?: toolbar)
    restoreFocusTargetRef = if (isFocusOutsideToolbar) WeakReference(focusOwner) else null
    IdeFocusManager.getGlobalInstance().requestFocusInProject(component, ProjectUtil.getProjectForComponent(toolbar))
  }

  fun registerComponent(component: Component) {
    for (item in mainToolbarComponents(component)) {
      installListeners(item)
      if (item.isToolbarFocusableType()) {
        item.isFocusable = true
        // To prevent mouse clicks from moving focus to the component
        if (item is JComponent) item.isRequestFocusEnabled = false
        when (item) {
          is ActionButton -> registerEnterAction(item) { item.click() }
          is AbstractButton -> registerEnterAction(item) { item.doClick() }
        }
      }
    }
  }

  fun getFocusableAndEnabledItems(): List<Component> = getFocusableItems().filter { it.isShowing && it.isEnabled }

  private fun getFocusableItems(): List<Component> =
    mainToolbarComponents(toolbar).filter { it.isFocusable && it.isToolbarFocusableType() }

  private fun registerEnterAction(component: JComponent, onEnter: () -> Unit) {
    if (component.getClientProperty(MAIN_TOOLBAR_FOCUS_REGISTRATION) != null) return
    DumbAwareAction.create { onEnter() }
      .registerCustomShortcutSet(CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0)), component, newRegistration(component))
  }

  /**
   * Ends the registrations of [component] and of each component inside it.
   * A container that this support observes calls it when [component] leaves that container.
   */
  private fun unregisterComponent(component: Component) {
    for (item in mainToolbarComponents(component)) {
      if (item !is JComponent) continue
      val registration = item.getClientProperty(MAIN_TOOLBAR_FOCUS_REGISTRATION) as? Disposable ?: continue
      item.putClientProperty(MAIN_TOOLBAR_FOCUS_REGISTRATION, null)
      Disposer.dispose(registration)
    }
  }

  /**
   * Makes the owner of the listeners or the Enter shortcut of [component] and keeps it in a client property of [component].
   * The owner ends when [component] leaves the main toolbar, so a header toolbar that the main toolbar replaces keeps no registration.
   */
  private fun newRegistration(component: JComponent): Disposable {
    val registration = Disposer.newDisposable(parentDisposable, "main toolbar focus registration")
    component.putClientProperty(MAIN_TOOLBAR_FOCUS_REGISTRATION, registration)
    return registration
  }

  fun restoreFocusToPreviousComponent() {
    cancelFocusFallback()
    val project = ProjectUtil.getProjectForComponent(toolbar) ?: return
    val savedFocus = restoreFocusTargetRef?.get()
    restoreFocusTargetRef = null
    if (savedFocus != null && savedFocus.isShowing && savedFocus.isEnabled) {
      IdeFocusManager.getGlobalInstance().requestFocusInProject(savedFocus, project)
      return
    }

    val toolWindowManager = ToolWindowManager.getInstance(project)
    toolWindowManager.activateEditorComponent()
    SwingUtilities.invokeLater {
      if (!toolWindowManager.isEditorComponentActive) {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().clearGlobalFocusOwner()
      }
    }
  }

  private fun cancelFocusFallback() {
    pendingFocusFallbackIndex = null
    focusFallbackScheduled = false
  }

  private fun installListeners(component: Component) {
    if (component !is JComponent ||
        (component !== toolbar && component !is ActionToolbar) ||
        component.getClientProperty(MAIN_TOOLBAR_FOCUS_REGISTRATION) != null) {
      return
    }

    val registration = newRegistration(component)
    val childRegistrationListener = object : ContainerAdapter() {
      override fun componentAdded(event: ContainerEvent) {
        registerComponent(event.child)
      }

      override fun componentRemoved(event: ContainerEvent) {
        unregisterComponent(event.child)
      }
    }
    component.addContainerListener(childRegistrationListener)
    Disposer.register(registration) {
      component.removeContainerListener(childRegistrationListener)
    }

    if (component is ActionToolbar) {
      component.addListener(object : ActionToolbarListener {
        // Follow action updates to move focus when an action button becomes disabled or is removed
        override fun actionsUpdated() {
          moveFocusToFallbackIfNeeded()
        }
      }, registration)
    }
  }

  private fun moveFocusToFallbackIfNeeded() {
    if (!isFocusInsideToolbar() || focusFallbackScheduled) return
    val focusedItem = currentFocusOwner() ?: return
    val focusedIndex = getFocusableItems().indexOf(focusedItem)
    if (focusedIndex < 0) {
      return
    }

    focusFallbackScheduled = true
    SwingUtilities.invokeLater {
      if (!focusFallbackScheduled) return@invokeLater
      focusFallbackScheduled = false
      if (!toolbar.isShowing) {
        return@invokeLater
      }
      if (!isFocusInsideToolbar()) {
        pendingFocusFallbackIndex = null
        return@invokeLater
      }
      val recoveryIndex = pendingFocusFallbackIndex ?: focusedIndex
      pendingFocusFallbackIndex = null
      val target = findSameIndexOrFallbackFocusableItem(getFocusableItems(), recoveryIndex) ?: return@invokeLater
      IdeFocusManager.getGlobalInstance().requestFocusInProject(target, ProjectUtil.getProjectForComponent(toolbar))
    }
  }

  private fun findSameIndexOrFallbackFocusableItem(items: List<Component>, index: Int): Component? {
    fun Component.isSelectable(): Boolean = isShowing && isEnabled
    val sameIndexItem = items.getOrNull(index)
    if (sameIndexItem != null && sameIndexItem.isSelectable()) {
      return sameIndexItem
    }
    val leftStart = minOf(index - 1, items.lastIndex)
    for (i in leftStart downTo 0) {
      if (items[i].isSelectable()) {
        return items[i]
      }
    }
    val rightStart = maxOf(index + 1, 0)
    for (i in rightStart until items.size) {
      if (items[i].isSelectable()) {
        return items[i]
      }
    }
    return null
  }

  private fun isFocusInsideToolbar(): Boolean {
    val owner = currentFocusOwner() ?: return false
    val toolbarFrameHeader = getToolbarFrameHeader()
    return SwingUtilities.isDescendingFrom(owner, toolbar) ||
           (toolbarFrameHeader != null && SwingUtilities.isDescendingFrom(owner, toolbarFrameHeader))
  }

  private fun getToolbarFrameHeader(): ToolbarFrameHeader? =
    SwingUtilities.getAncestorOfClass(ToolbarFrameHeader::class.java, toolbar) as? ToolbarFrameHeader

  private inner class MainToolbarFocusTraversalPolicy : ComponentsListFocusTraversalPolicy(true) {
    override fun getOrderedComponents(): List<Component> = getFocusableAndEnabledItems()

    override fun getComponentAfter(aContainer: Container, aComponent: Component): Component? =
      super.getComponentAfter(aContainer, aComponent)
      ?: getToolbarFrameHeader()?.getMenuButtonFocusTarget()
      ?: getFirstComponent(aContainer)

    override fun getComponentBefore(aContainer: Container, aComponent: Component): Component? =
      super.getComponentBefore(aContainer, aComponent)
      ?: getToolbarFrameHeader()?.getMenuButtonFocusTarget()
      ?: getLastComponent(aContainer)

    // Prevents from being focused automatically
    override fun getDefaultComponent(aContainer: Container): Component? = null
  }
}

internal fun installHeaderToolbarFocusTraversalPolicy(container: Container, policy: FocusTraversalPolicy) {
  container.isFocusCycleRoot = true
  container.isFocusTraversalPolicyProvider = true
  container.focusTraversalPolicy = policy
  val kfm = KeyboardFocusManager.getCurrentKeyboardFocusManager()
  container.setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS,
                                  kfm.getDefaultFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS) +
                                  AWTKeyStroke.getAWTKeyStroke(KeyEvent.VK_RIGHT, 0))
  container.setFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS,
                                  kfm.getDefaultFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS) +
                                  AWTKeyStroke.getAWTKeyStroke(KeyEvent.VK_LEFT, 0))
}

private fun mainToolbarComponents(component: Component): Iterable<Component> =
  UIUtil.uiTraverser(component).expandAndFilter { it !is ActionMenu }.traverse()

private fun Component.isToolbarFocusableType(): Boolean =
  when (this) {
    is ActionMenu -> false
    is ActionButton, is ToolbarComboButton, is ToolbarSplitButton, is AbstractButton -> true
    else -> false
  }

private fun currentFocusOwner(): Component? = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
