// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui

import com.intellij.openapi.application.EDT
import com.intellij.platform.util.coroutines.childScope
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.common.waitUntil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.ui.EDT
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration.Companion.seconds

/** Checks the value listeners of [UISettings], such as [UISettings.addEditorAntialiasingListener]. */
@TestApplication
class UISettingsValueListenerTest {
  private val settings: UISettings
    get() = UISettings.getInstance()

  @BeforeEach
  @AfterEach
  fun dropStoredSettings(): Unit = timeoutRunBlocking(60.seconds) {
    dropStoredUISettings()
  }

  @Test
  fun `a setter write calls the listener once with the new value on the EDT`(): Unit = timeoutRunBlocking(60.seconds) {
    val newValue = otherAntialiasing(settings.editorAAType)
    val calls = editorAntialiasingCalls(settings, coroutineScope = this)

    settings.editorAAType = newValue

    assertEquals(listOf(Call(newValue, onEdt = true)), calls.waitForCalls(1))
  }

  @Test
  fun `a change of another value does not call the listener`(): Unit = timeoutRunBlocking(60.seconds) {
    val newValue = otherAntialiasing(settings.editorAAType)
    val calls = editorAntialiasingCalls(settings, coroutineScope = this)

    writeUISettingsKey(UISettingsKeyIdentities.IDE_ANTIALIASING, otherAntialiasing(settings.ideAAType))
    // The value listeners run on the EDT in the order of the changes, so this marker comes after any call of the change above.
    writeUISettingsKey(UISettingsKeyIdentities.EDITOR_ANTIALIASING, newValue)

    assertEquals(listOf(Call(newValue, onEdt = true)), calls.waitForCalls(1))
  }

  @Test
  fun `a listener of a cancelled scope is not called`(): Unit = timeoutRunBlocking(60.seconds) {
    val newValue = otherAntialiasing(settings.editorAAType)
    val cancelledListenerScope = childScope("the cancelled listener")
    val cancelledListenerCalls = editorAntialiasingCalls(settings, cancelledListenerScope)
    val markerCalls = editorAntialiasingCalls(settings, coroutineScope = this)
    cancelledListenerScope.cancel()

    writeUISettingsKey(UISettingsKeyIdentities.EDITOR_ANTIALIASING, newValue)

    markerCalls.waitForCalls(1)
    assertEquals(emptyList<Call>(), cancelledListenerCalls)
  }

  @Test
  fun `a key change that keeps the value does not call the listener`(): Unit = timeoutRunBlocking(60.seconds) {
    val value = settings.editorAAType
    val calls = editorAntialiasingCalls(settings, coroutineScope = this)

    // The key stores no value, so a write of the value that it reads changes the stored value only.
    writeUISettingsKey(UISettingsKeyIdentities.EDITOR_ANTIALIASING, value)
    writeUISettingsKey(UISettingsKeyIdentities.EDITOR_ANTIALIASING, otherAntialiasing(value))

    assertEquals(listOf(Call(otherAntialiasing(value), onEdt = true)), calls.waitForCalls(1))
  }

  @Test
  fun `an instance over a given component registers no listener`(): Unit = timeoutRunBlocking(60.seconds) {
    val newValue = otherAntialiasing(settings.editorAAType)
    val component = NotRoamableUiSettings()
    val instanceCalls = editorAntialiasingCalls(UISettings(component), coroutineScope = this)
    val markerCalls = editorAntialiasingCalls(settings, coroutineScope = this)
    // A listener of the instance reads its component, so a registered one would get this value at the key change.
    component.editorAAType = otherAntialiasing(component.editorAAType)

    writeUISettingsKey(UISettingsKeyIdentities.EDITOR_ANTIALIASING, newValue)

    markerCalls.waitForCalls(1)
    assertEquals(emptyList<Call>(), instanceCalls)
  }

  /** Holds one call of a value listener: the value it got, and whether it ran on the EDT. */
  private data class Call(val value: AntialiasingType, val onEdt: Boolean)

  /** Adds an editor antialiasing listener to [settings] and returns the list of its calls. */
  private fun editorAntialiasingCalls(settings: UISettings, coroutineScope: CoroutineScope): List<Call> {
    val calls = CopyOnWriteArrayList<Call>()
    settings.addEditorAntialiasingListener(coroutineScope) { calls.add(Call(it, EDT.isCurrentThreadEdt())) }
    return calls
  }

  /** Waits until this list holds [count] calls, lets the EDT pass the calls that are on the way, and returns the calls. */
  private suspend fun <T> List<T>.waitForCalls(count: Int): List<T> {
    waitUntil("the listener gets $count call(s)") { size >= count }
    withContext(Dispatchers.EDT) {}
    return toList()
  }

  /** Returns an antialiasing that differs from [value]. */
  private fun otherAntialiasing(value: AntialiasingType): AntialiasingType =
    if (value == AntialiasingType.OFF) AntialiasingType.GREYSCALE else AntialiasingType.OFF
}
