@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package com.intellij.util.ui

import com.intellij.openapi.application.UI
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.ui.components.MacScrollBarUI
import com.intellij.ui.components.MacScrollbarStyle
import com.intellij.ui.mac.foundation.Foundation
import com.intellij.ui.mac.foundation.ID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS.MAC
import java.awt.event.MouseEvent
import java.lang.foreign.MemorySegment
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.JScrollBar
import javax.swing.SwingUtilities

@TestApplication
@Timeout(30)
internal class MacScrollbarPreferencesTest {
  @Test
  @DisabledOnOs(MAC)
  fun `non-Mac systems retain the fallback preferences`() {
    assertThat(NSScrollerHelper.getClickBehavior()).isNull()
    assertThat(NSScrollerHelper.getScrollerStyle()).isEqualTo(NSScrollerHelper.Style.Overlay)
  }

  @Test
  @EnabledOnOs(MAC)
  fun `native preferences match JNA`() {
    withNativePool {
      val style = Foundation.invoke("NSScroller", "preferredScrollerStyle").toLong()
      val defaults = Foundation.invoke("NSUserDefaults", "standardUserDefaults")
      Foundation.invoke(defaults, "synchronize")
      val behavior = Foundation.invoke(defaults, "boolForKey:", Foundation.nsString("AppleScrollerPagingBehavior")).booleanValue()
      assertThat(MacScrollbarPreferences.getPreferredStyle()).isEqualTo(style)
      assertThat(MacScrollbarPreferences.isJumpToSpot()).isEqualTo(behavior)
      val expectedStyle = if (style == 1L) NSScrollerHelper.Style.Overlay else NSScrollerHelper.Style.Legacy
      assertThat(NSScrollerHelper.getScrollerStyle()).isEqualTo(expectedStyle)
    }
  }

  @Test
  @EnabledOnOs(MAC)
  fun `style observers survive registration and close independently`(): Unit = timeoutRunBlocking {
    val firstCalls = AtomicInteger()
    val secondCalls = AtomicInteger()
    val deliveries = Channel<Boolean>(Channel.UNLIMITED)
    val first = MacScrollbarPreferences.observeStyleChanges { firstCalls.incrementAndGet() }
    val second = MacScrollbarPreferences.observeStyleChanges {
      secondCalls.incrementAndGet()
      deliveries.trySend(SwingUtilities.isEventDispatchThread())
    }
    try {
      repeat(3) {
        postStyleChange()
        assertThat(deliveries.receive()).isTrue()
        withContext(Dispatchers.UI) {}
      }
      assertThat(firstCalls.get()).isEqualTo(3)
      first.close()
      first.close()
      postStyleChange()
      assertThat(deliveries.receive()).isTrue()
      assertThat(firstCalls.get()).isEqualTo(3)
      assertThat(secondCalls.get()).isEqualTo(4)
      second.close()
      postStyleChange()
      withContext(Dispatchers.UI) {
        assertThat(secondCalls.get()).isEqualTo(4)
      }
    }
    finally {
      first.close()
      second.close()
      deliveries.close()
    }
  }

  @Test
  @EnabledOnOs(MAC)
  fun `distributed observers dispatch on the EDT and read changed preferences`(): Unit = timeoutRunBlocking {
    for (jumpToSpot in listOf(false, true)) {
      withContext(Dispatchers.UI) {
        withPagingBehavior(jumpToSpot) {
          var delivered: Boolean? = null
          MacScrollbarPreferences.observeBehaviorChanges {
            assertThat(SwingUtilities.isEventDispatchThread()).isTrue()
            delivered = MacScrollbarPreferences.isJumpToSpot()
          }.use { observer ->
            deliverNotification(observer)
            assertThat(delivered).isEqualTo(jumpToSpot)
          }
        }
      }
    }
  }

  @Test
  @EnabledOnOs(MAC)
  fun `listener failures do not escape the native upcall`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.UI) {
      MacScrollbarPreferences.observeStyleChanges { throw IllegalStateException("Expected scrollbar listener failure") }.use {
        postStyleChange()
      }
      var called = false
      MacScrollbarPreferences.observeStyleChanges { called = true }.use {
        postStyleChange()
        assertThat(called).isTrue()
      }
    }
  }

  @Test
  @EnabledOnOs(MAC)
  fun `the helper forwards style notifications on the EDT`(): Unit = timeoutRunBlocking {
    val delivery = CompletableDeferred<Boolean>()
    val listener = NSScrollerHelper.ScrollbarStyleListener { delivery.complete(SwingUtilities.isEventDispatchThread()) }
    NSScrollerHelper.addScrollbarStyleListener(listener)
    try {
      postStyleChange()
      assertThat(delivery.await()).isTrue()
    }
    finally {
      NSScrollerHelper.removeScrollbarStyleListener(listener)
    }
  }

  @Test
  @EnabledOnOs(MAC)
  fun `the scrollbar supports both styles and paging behaviors`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.UI) {
      val scrollbar = JScrollBar()
      val ui = TestScrollBarUI()
      scrollbar.setUI(ui)
      try {
        ui.applyStyle(MacScrollbarStyle.Overlay)
        assertThat(scrollbar.isOpaque).isFalse()
        ui.applyStyle(MacScrollbarStyle.Legacy)
        assertThat(scrollbar.isOpaque).isTrue()
        val event = MouseEvent(scrollbar, MouseEvent.MOUSE_PRESSED, 0, 0, 0, 0, 1, false)
        for (jumpToSpot in listOf(false, true)) {
          withPagingBehavior(jumpToSpot) {
            assertThat(ui.isAbsolutePositioning(event)).isEqualTo(jumpToSpot)
          }
        }
      }
      finally {
        scrollbar.setUI(null)
      }
    }
  }

  private class TestScrollBarUI : MacScrollBarUI() {
    fun applyStyle(style: MacScrollbarStyle) {
      updateStyle(style)
    }
  }

  private fun postStyleChange() {
    withNativePool {
      val center = Foundation.invoke("NSNotificationCenter", "defaultCenter")
      Foundation.invoke(center, "postNotificationName:object:",
                        Foundation.nsString("NSPreferredScrollerStyleDidChangeNotification"), ID.NIL)
    }
  }

  private fun deliverNotification(observer: AutoCloseable) {
    val field = observer.javaClass.getDeclaredField("delegate")
    field.isAccessible = true
    val delegate = field.get(observer) as MemorySegment
    withNativePool {
      Foundation.invoke(ID(delegate.address()), "notificationReceived:", ID.NIL)
    }
  }

  private fun withPagingBehavior(jumpToSpot: Boolean, action: () -> Unit) {
    withNativePool {
      val defaults = Foundation.invoke("NSUserDefaults", "standardUserDefaults")
      val domain = Foundation.nsString("NSArgumentDomain")
      val original = Foundation.invoke(defaults, "volatileDomainForName:", domain)
      Foundation.invoke(original, "retain")
      val replacement = Foundation.invoke(original, "mutableCopy")
      try {
        val value = Foundation.invoke("NSNumber", "numberWithBool:", if (jumpToSpot) 1 else 0)
        Foundation.invoke(replacement, "setObject:forKey:", value, Foundation.nsString("AppleScrollerPagingBehavior"))
        Foundation.invoke(defaults, "setVolatileDomain:forName:", replacement, domain)
        action()
      }
      finally {
        Foundation.invoke(defaults, "setVolatileDomain:forName:", original, domain)
        Foundation.invoke(replacement, "release")
        Foundation.invoke(original, "release")
      }
    }
  }

  private fun <T> withNativePool(action: () -> T): T {
    val pool = Foundation.NSAutoreleasePool()
    try {
      return action()
    }
    finally {
      pool.drain()
    }
  }
}
