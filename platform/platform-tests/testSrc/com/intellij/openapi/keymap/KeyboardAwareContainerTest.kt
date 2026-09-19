// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.keymap

import com.intellij.ide.DataManager
import com.intellij.ide.KeyboardAwareContainer
import com.intellij.ide.KeyboardAwareFocusOwner
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.application.EDT
import com.intellij.openapi.keymap.ex.KeymapManagerEx
import com.intellij.openapi.keymap.impl.IdeKeyEventDispatcher
import com.intellij.openapi.keymap.impl.KeymapImpl
import com.intellij.testFramework.common.runAll
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.awt.Component
import java.awt.DefaultKeyboardFocusManager
import java.awt.KeyboardFocusManager
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.KeyStroke

/**
 * Covers the [KeyboardAwareContainer] escape hatch: an ancestor of the focus owner may keep [IdeKeyEventDispatcher] from
 * running keymap shortcuts on behalf of a focused component it cannot control, such as the internal canvas of an
 * embedded renderer.
 */
@TestApplication
@Timeout(30)
internal class KeyboardAwareContainerTest {
  private companion object {
    const val KEYMAP_NAME = "KeyboardAwareContainerTestKeymap"
    const val SINGLE_ACTION = "!!!ContainerTestSingleAction"
    const val CHORD_ACTION = "!!!ContainerTestChordAction"

    val SINGLE_STROKE: KeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, InputEvent.ALT_DOWN_MASK)
    val CHORD_FIRST: KeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_K, InputEvent.CTRL_DOWN_MASK)
    val CHORD_SECOND: KeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_D, InputEvent.CTRL_DOWN_MASK)
  }

  private class CountingAction : AnAction() {
    val invocations = AtomicInteger()

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun actionPerformed(e: AnActionEvent) {
      invocations.incrementAndGet()
    }
  }

  /** Stands in for the internal focused component of an embedded renderer. */
  private class InnerComponent : JTextField()

  private class SkippingInnerComponent(private val skip: Boolean) : JTextField(), KeyboardAwareFocusOwner {
    override fun skipKeyEventDispatcher(event: KeyEvent): Boolean = skip
  }

  /** Records every consultation into the shared [calls] log, tagged with [name], then answers with [claim]. */
  private inner class ContainerPanel(private val name: String) : JPanel(), KeyboardAwareContainer {
    var claim: (KeyEvent) -> Boolean = { false }
    var lastFocusOwner: Component? = null

    override fun skipKeyEventDispatcher(focusOwner: Component, event: KeyEvent): Boolean {
      calls.add(name)
      lastFocusOwner = focusOwner
      return claim(event)
    }
  }

  /**
   * Pins the focus owner without showing a window: the dispatcher reads it from the current [KeyboardFocusManager]. There
   * is no focused window in a headless test, so dispatch runs outside any modal context.
   */
  private class StubFocusManager(private val owner: Component) : DefaultKeyboardFocusManager() {
    override fun getFocusOwner(): Component = owner
  }

  private val calls = mutableListOf<String>()
  private lateinit var originalFocusManager: KeyboardFocusManager
  private lateinit var keymap: KeymapImpl
  private lateinit var savedKeymap: Keymap
  private lateinit var singleAction: CountingAction
  private lateinit var chordAction: CountingAction

  @BeforeEach
  fun setUp() {
    // dispatchKeyEvent silently returns false when the DataManager service was never instantiated
    DataManager.getInstance()
    originalFocusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()

    keymap = KeymapImpl()
    keymap.name = KEYMAP_NAME
    KeymapManagerEx.getInstanceEx().schemeManager.addScheme(keymap, false)
    savedKeymap = KeymapManagerEx.getInstanceEx().activeKeymap
    KeymapManagerEx.getInstanceEx().activeKeymap = keymap

    singleAction = CountingAction()
    chordAction = CountingAction()
    ActionManager.getInstance().registerAction(SINGLE_ACTION, singleAction)
    ActionManager.getInstance().registerAction(CHORD_ACTION, chordAction)
    keymap.addShortcut(SINGLE_ACTION, KeyboardShortcut(SINGLE_STROKE, null))
    keymap.addShortcut(CHORD_ACTION, KeyboardShortcut(CHORD_FIRST, CHORD_SECOND))
  }

  @AfterEach
  fun tearDown() {
    runAll(
      { KeyboardFocusManager.setCurrentKeyboardFocusManager(originalFocusManager) },
      { ActionManager.getInstance().unregisterAction(SINGLE_ACTION) },
      { ActionManager.getInstance().unregisterAction(CHORD_ACTION) },
      { KeymapManagerEx.getInstanceEx().activeKeymap = savedKeymap },
      { KeymapManagerEx.getInstanceEx().schemeManager.removeScheme(keymap) },
    )
  }

  /** Builds `container > inner`, focuses `inner`, and returns both. */
  private fun focusedHierarchy(inner: Component = InnerComponent()): Pair<ContainerPanel, Component> {
    val container = ContainerPanel("container")
    container.add(inner)
    focus(inner)
    return container to inner
  }

  private fun focus(component: Component) {
    KeyboardFocusManager.setCurrentKeyboardFocusManager(StubFocusManager(component))
  }

  private fun pressed(source: Component, stroke: KeyStroke): KeyEvent =
    KeyEvent(source, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), stroke.modifiers, stroke.keyCode, KeyEvent.CHAR_UNDEFINED)

  private fun released(source: Component, stroke: KeyStroke): KeyEvent =
    KeyEvent(source, KeyEvent.KEY_RELEASED, System.currentTimeMillis(), stroke.modifiers, stroke.keyCode, KeyEvent.CHAR_UNDEFINED)

  private fun typed(source: Component, char: Char): KeyEvent =
    KeyEvent(source, KeyEvent.KEY_TYPED, System.currentTimeMillis(), 0, KeyEvent.VK_UNDEFINED, char)

  @Test
  fun `no container in the hierarchy leaves dispatch unchanged`(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    val inner = InnerComponent()
    JPanel().add(inner)
    focus(inner)

    val dispatcher = IdeKeyEventDispatcher(null)
    assertTrue(dispatcher.dispatchKeyEvent(pressed(inner, SINGLE_STROKE)))
    assertEquals(1, singleAction.invocations.get())
  }

  @Test
  fun `a claim prevents the keymap action and leaves the event unconsumed`(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    val (container, inner) = focusedHierarchy()
    container.claim = { true }

    val event = pressed(inner, SINGLE_STROKE)
    assertFalse(IdeKeyEventDispatcher(null).dispatchKeyEvent(event), "a skipped event must not be reported as dispatched")
    assertFalse(event.isConsumed, "the container keeps the event from the keymap; it must not consume it")
    assertEquals(0, singleAction.invocations.get())
    assertEquals(inner, container.lastFocusOwner)
  }

  @Test
  fun `a declining container falls through to normal processing`(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    val (container, inner) = focusedHierarchy()
    container.claim = { false }

    assertTrue(IdeKeyEventDispatcher(null).dispatchKeyEvent(pressed(inner, SINGLE_STROKE)))
    assertEquals(1, singleAction.invocations.get())
    assertEquals(listOf("container"), calls, "the container must have been consulted exactly once")
  }

  @Test
  fun `a focus owner that skips as KeyboardAwareFocusOwner wins before any container is asked`(): Unit =
    timeoutRunBlocking(context = Dispatchers.EDT) {
      val (container, inner) = focusedHierarchy(inner = SkippingInnerComponent(skip = true))
      container.claim = { true }

      assertFalse(IdeKeyEventDispatcher(null).dispatchKeyEvent(pressed(inner, SINGLE_STROKE)))
      assertEquals(0, singleAction.invocations.get())
      assertTrue(calls.isEmpty(), "the exact-focus-owner escape hatch must take precedence")
    }

  @Test
  fun `a focus owner that declines as KeyboardAwareFocusOwner still lets a container claim`(): Unit =
    timeoutRunBlocking(context = Dispatchers.EDT) {
      val (container, inner) = focusedHierarchy(inner = SkippingInnerComponent(skip = false))
      container.claim = { true }

      assertFalse(IdeKeyEventDispatcher(null).dispatchKeyEvent(pressed(inner, SINGLE_STROKE)))
      assertEquals(0, singleAction.invocations.get())
      assertEquals(listOf("container"), calls)
    }

  @Test
  fun `containers are asked innermost first, and an inner claim stops the walk`(): Unit =
    timeoutRunBlocking(context = Dispatchers.EDT) {
      val inner = InnerComponent()
      val innerContainer = ContainerPanel("inner")
      val outerContainer = ContainerPanel("outer")
      JPanel().also { it.add(inner); innerContainer.add(it) }
      JPanel().also { it.add(innerContainer); outerContainer.add(it) }
      focus(inner)
      innerContainer.claim = { false }
      outerContainer.claim = { true }

      val dispatcher = IdeKeyEventDispatcher(null)
      assertFalse(dispatcher.dispatchKeyEvent(pressed(inner, SINGLE_STROKE)))
      assertEquals(listOf("inner", "outer"), calls)

      innerContainer.claim = { true }
      calls.clear()
      assertFalse(dispatcher.dispatchKeyEvent(pressed(inner, SINGLE_STROKE)))
      assertEquals(listOf("inner"), calls)
      assertEquals(0, singleAction.invocations.get())
    }

  @Test
  fun `the container decides every event even though the chain is resolved once`(): Unit =
    timeoutRunBlocking(context = Dispatchers.EDT) {
      val (container, inner) = focusedHierarchy()
      val dispatcher = IdeKeyEventDispatcher(null)

      container.claim = { true }
      assertFalse(dispatcher.dispatchKeyEvent(pressed(inner, SINGLE_STROKE)))
      container.claim = { false }
      assertTrue(dispatcher.dispatchKeyEvent(pressed(inner, SINGLE_STROKE)))
      container.claim = { true }
      assertFalse(dispatcher.dispatchKeyEvent(pressed(inner, SINGLE_STROKE)))

      assertEquals(1, singleAction.invocations.get())
      assertEquals(listOf("container", "container", "container"), calls)
    }

  @Test
  fun `a different focus owner re-resolves the chain instead of reusing the previous one`(): Unit =
    timeoutRunBlocking(context = Dispatchers.EDT) {
      val (container, inner) = focusedHierarchy()
      container.claim = { true }

      // A second focus owner with no container above it: reusing the first chain would wrongly claim here.
      val bareInner = InnerComponent()
      JPanel().add(bareInner)

      val dispatcher = IdeKeyEventDispatcher(null)
      assertFalse(dispatcher.dispatchKeyEvent(pressed(inner, SINGLE_STROKE)))
      assertEquals(0, singleAction.invocations.get())

      focus(bareInner)
      assertTrue(dispatcher.dispatchKeyEvent(pressed(bareInner, SINGLE_STROKE)))
      assertEquals(1, singleAction.invocations.get(), "the container-less owner is dispatched normally")
    }

  @Test
  fun `a focus owner moved out from under its container is re-resolved`(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    val (container, inner) = focusedHierarchy()
    container.claim = { true }
    container.add(JPanel())

    val dispatcher = IdeKeyEventDispatcher(null)
    assertFalse(dispatcher.dispatchKeyEvent(pressed(inner, SINGLE_STROKE)))

    // Reordering inside the same parent does not change the ancestor chain.
    container.setComponentZOrder(inner, 1)
    assertFalse(dispatcher.dispatchKeyEvent(pressed(inner, SINGLE_STROKE)))
    assertEquals(0, singleAction.invocations.get())

    JPanel().setComponentZOrder(inner, 0)
    assertTrue(dispatcher.dispatchKeyEvent(pressed(inner, SINGLE_STROKE)))
    assertEquals(1, singleAction.invocations.get(), "the old container must not claim any more")
  }

  @Test
  fun `a container added above an already focused owner is seen`(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    val inner = InnerComponent()
    val plain = JPanel()
    plain.add(inner)
    focus(inner)

    val dispatcher = IdeKeyEventDispatcher(null)
    assertTrue(dispatcher.dispatchKeyEvent(pressed(inner, SINGLE_STROKE)), "no container yet: the cached answer is 'none'")
    assertEquals(1, singleAction.invocations.get())

    val container = ContainerPanel("container")
    container.claim = { true }
    container.add(plain)
    assertFalse(dispatcher.dispatchKeyEvent(pressed(inner, SINGLE_STROKE)))
    assertEquals(1, singleAction.invocations.get(), "the new ancestor claims without any focus change")
    assertEquals(listOf("container"), calls)
  }

  @Test
  fun `an outer container replaced while the inner one stays is re-resolved`(): Unit =
    timeoutRunBlocking(context = Dispatchers.EDT) {
      val inner = InnerComponent()
      val innerContainer = ContainerPanel("inner")
      val oldOuter = ContainerPanel("old outer")
      innerContainer.add(inner)
      oldOuter.add(innerContainer)
      focus(inner)
      innerContainer.claim = { false }
      oldOuter.claim = { true }

      val dispatcher = IdeKeyEventDispatcher(null)
      assertFalse(dispatcher.dispatchKeyEvent(pressed(inner, SINGLE_STROKE)))
      assertEquals(listOf("inner", "old outer"), calls)

      val newOuter = ContainerPanel("new outer")
      newOuter.claim = { false }
      oldOuter.remove(innerContainer)
      newOuter.add(innerContainer)
      calls.clear()
      assertTrue(dispatcher.dispatchKeyEvent(pressed(inner, SINGLE_STROKE)))
      assertEquals(listOf("inner", "new outer"), calls, "the removed outer container must not be asked any more")
    }

  @Test
  fun `a container inserted below the cached innermost one is asked first`(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    val inner = InnerComponent()
    val outer = ContainerPanel("outer")
    outer.add(inner)
    focus(inner)
    outer.claim = { false }

    val dispatcher = IdeKeyEventDispatcher(null)
    assertTrue(dispatcher.dispatchKeyEvent(pressed(inner, SINGLE_STROKE)))
    assertEquals(listOf("outer"), calls)

    val inserted = ContainerPanel("inserted")
    inserted.claim = { true }
    inserted.add(inner)
    outer.add(inserted)
    calls.clear()
    assertFalse(dispatcher.dispatchKeyEvent(pressed(inner, SINGLE_STROKE)))
    assertEquals(listOf("inserted"), calls)
  }

  @Test
  fun `a hierarchy change while focus is elsewhere is seen when focus returns`(): Unit =
    timeoutRunBlocking(context = Dispatchers.EDT) {
      val (container, inner) = focusedHierarchy()
      container.claim = { true }
      val elsewhere = InnerComponent()
      JPanel().add(elsewhere)

      val dispatcher = IdeKeyEventDispatcher(null)
      assertFalse(dispatcher.dispatchKeyEvent(pressed(inner, SINGLE_STROKE)))

      // Focus leaves, the hierarchy changes without any key event, and focus returns to the same owner.
      focus(elsewhere)
      JPanel().add(inner)
      focus(inner)
      assertTrue(dispatcher.dispatchKeyEvent(pressed(inner, SINGLE_STROKE)))
      assertEquals(1, singleAction.invocations.get())
    }

  /** The listener must not pile up on an owner, and must not stay on an owner that lost focus. */
  @Test
  fun `the hierarchy listener is installed once per owner and removed on invalidation and on focus change`(): Unit =
    timeoutRunBlocking(context = Dispatchers.EDT) {
      val (container, inner) = focusedHierarchy()
      val baseline = inner.hierarchyListeners.size

      val dispatcher = IdeKeyEventDispatcher(null)
      repeat(3) { dispatcher.dispatchKeyEvent(pressed(inner, SINGLE_STROKE)) }
      assertEquals(baseline + 1, inner.hierarchyListeners.size, "one registration, reused across events")

      JPanel().add(inner)
      assertEquals(baseline, inner.hierarchyListeners.size, "reparenting removes the registration")
      dispatcher.dispatchKeyEvent(pressed(inner, SINGLE_STROKE))
      assertEquals(baseline + 1, inner.hierarchyListeners.size, "re-resolving installs it again, once")

      val other = InnerComponent()
      container.add(other)
      focus(other)
      dispatcher.dispatchKeyEvent(pressed(other, SINGLE_STROKE))
      assertEquals(baseline, inner.hierarchyListeners.size, "a focus change removes it from the previous owner")
      assertEquals(baseline + 1, other.hierarchyListeners.size)
    }

  /**
   * After the dispatcher performs an action for a press, it swallows the next `KEY_TYPED` itself, without asking the
   * container. After a claimed press it does not: the typed character still reaches the focused component, and the
   * embedder has to suppress it.
   */
  @Test
  fun `the typed event after a claimed press is not swallowed by the dispatcher`(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    val (container, inner) = focusedHierarchy()
    val dispatcher = IdeKeyEventDispatcher(null)

    // Baseline: an unclaimed press performs the action, and the typed event is swallowed without asking.
    container.claim = { false }
    assertTrue(dispatcher.dispatchKeyEvent(pressed(inner, SINGLE_STROKE)))
    calls.clear()
    assertTrue(dispatcher.dispatchKeyEvent(typed(inner, '<')), "typed event after a performed action is swallowed")
    assertTrue(calls.isEmpty(), "the dispatcher keeps the typed event of a shortcut it performed")
    assertFalse(dispatcher.dispatchKeyEvent(released(inner, SINGLE_STROKE)))

    // After a claimed press, the typed event is offered to the container and, when declined, leaks through.
    container.claim = { it.id == KeyEvent.KEY_PRESSED }
    assertFalse(dispatcher.dispatchKeyEvent(pressed(inner, SINGLE_STROKE)))
    assertFalse(dispatcher.dispatchKeyEvent(typed(inner, '<')), "typed event after a claimed press leaks through")
    assertEquals(0, singleAction.invocations.get() - 1, "only the baseline press performed the action")
  }

  /**
   * A container cannot take over a multi-stroke shortcut the IDE has already started: the pending chord completes without
   * asking the container, and only then does the dispatcher ask again. A claim has to start at a chord's first stroke.
   */
  @Test
  fun `a pending IDE chord keeps priority over a claim`(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    val (container, inner) = focusedHierarchy()
    val dispatcher = IdeKeyEventDispatcher(null)

    container.claim = { false }
    assertTrue(dispatcher.dispatchKeyEvent(pressed(inner, CHORD_FIRST)))
    assertTrue(dispatcher.isWaitingForSecondKeyStroke)

    container.claim = { true }
    calls.clear()
    assertTrue(dispatcher.dispatchKeyEvent(pressed(inner, CHORD_SECOND)), "the IDE completes its chord")
    assertEquals(1, chordAction.invocations.get())
    assertTrue(calls.isEmpty(), "the container is not asked while the IDE finishes a chord")

    assertFalse(dispatcher.dispatchKeyEvent(released(inner, CHORD_SECOND)))
    assertFalse(dispatcher.dispatchKeyEvent(pressed(inner, SINGLE_STROKE)), "once idle again, the dispatcher asks and honors the claim")
    assertEquals(0, singleAction.invocations.get())
  }

  @Test
  fun `a claim covering the whole chord never enters the pending state`(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    val (container, inner) = focusedHierarchy()
    val dispatcher = IdeKeyEventDispatcher(null)

    container.claim = { true }
    assertFalse(dispatcher.dispatchKeyEvent(pressed(inner, CHORD_FIRST)))
    assertFalse(dispatcher.isWaitingForSecondKeyStroke)
    assertFalse(dispatcher.dispatchKeyEvent(pressed(inner, CHORD_SECOND)))
    assertEquals(0, chordAction.invocations.get())

    container.claim = { false }
    assertTrue(dispatcher.dispatchKeyEvent(pressed(inner, CHORD_FIRST)))
    assertTrue(dispatcher.isWaitingForSecondKeyStroke)
    assertTrue(dispatcher.dispatchKeyEvent(pressed(inner, CHORD_SECOND)))
    assertEquals(1, chordAction.invocations.get())
  }
}
