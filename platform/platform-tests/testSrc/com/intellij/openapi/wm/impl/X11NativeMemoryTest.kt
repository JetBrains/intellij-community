package com.intellij.openapi.wm.impl

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy

import org.junit.jupiter.api.Test
import java.lang.foreign.Arena
import java.lang.foreign.ValueLayout.ADDRESS
import java.lang.foreign.ValueLayout.JAVA_INT
import java.lang.foreign.ValueLayout.JAVA_LONG

internal class X11NativeMemoryTest {
  @Test
  fun `client message uses the complete XEvent union`() {
    Arena.ofConfined().use { arena ->
      val event = X11NativeMemory.clientMessage(arena, 0x123456789, 41, 42, longArrayOf(1, Long.MIN_VALUE, 3, 4, 5))
      assertThat(event.byteSize()).isEqualTo(192L)
      assertThat(event.get(JAVA_INT, 0)).isEqualTo(33)
      assertThat(event.get(JAVA_INT, 16)).isEqualTo(1)
      assertThat(event.get(ADDRESS, 24).address()).isEqualTo(0x123456789L)
      assertThat(event.get(JAVA_LONG, 32)).isEqualTo(41L)
      assertThat(event.get(JAVA_LONG, 40)).isEqualTo(42L)
      assertThat(event.get(JAVA_INT, 48)).isEqualTo(32)
      assertThat(event.get(JAVA_LONG, 64)).isEqualTo(Long.MIN_VALUE)
      assertThat(event.get(JAVA_LONG, 88)).isEqualTo(5L)
      assertThat(event.get(JAVA_LONG, 184)).isEqualTo(0L)
    }
  }

  @Test
  fun `missing fields are zero and excess data is rejected`() {
    Arena.ofConfined().use { arena ->
      val event = X11NativeMemory.clientMessage(arena, 1, 2, 3, longArrayOf(4))
      assertThat(event.get(JAVA_LONG, 64)).isEqualTo(0L)
      assertThatThrownBy { X11NativeMemory.clientMessage(arena, 1, 2, 3, LongArray(6)) }
        .isInstanceOf(IllegalArgumentException::class.java)
    }
  }
}
