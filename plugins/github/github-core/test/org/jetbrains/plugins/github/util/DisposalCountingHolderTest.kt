// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.util

import com.intellij.collaboration.async.cancelAndJoin
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class DisposalCountingHolderTest {
  private data class CancellableData(
    val cs: CoroutineScope,
  )

  @Test
  fun `no value is created before acquiring`() = runTest {
    launch {
      val holderScope = this.childScope("holder")
      val holder = AcquirableScopedValueOwner(holderScope) { CancellableData(this) }

      assertThat(holder.value).isNull()

      cancelAndJoin()
    }
  }

  @Test
  fun `acquiring the value twice doesn't recreate it`() = runTest {
    launch {
      val holderScope = this.childScope("holder")
      val holder = AcquirableScopedValueOwner(holderScope) { CancellableData(this) }

      val host1 = childScope("host1")
      val host2 = childScope("host2")

      val v1 = holder.acquireValue(host1)
      val v2 = holder.acquireValue(host2)

      assertSame(v1, v2)
      assertThat(v1.cs.isActive).isTrue()

      cancelAndJoin()
    }
  }

  @Test
  fun `acquiring the value twice, then cancelling one doesn't release it`() = runTest {
    launch {
      val holderScope = this.childScope("holder")
      val holder = AcquirableScopedValueOwner(holderScope) { CancellableData(this) }

      val host1 = childScope("host1")
      val host2 = childScope("host2")

      holder.acquireValue(host1)
      val v2 = holder.acquireValue(host2)

      host1.cancelAndJoin()
      assertThat(v2.cs.isActive).isTrue()
      assertThat(holder.value).isNotNull()

      cancelAndJoin()
    }
  }

  @Test
  fun `acquiring the value once, then releasing it, releases the value`() = runTest {
    launch {
      val holderScope = this.childScope("holder")
      val holder = AcquirableScopedValueOwner(holderScope) { CancellableData(this) }

      val host1 = childScope("host1")

      val v1 = holder.acquireValue(host1)
      host1.cancelAndJoin()

      assertThat(v1.cs.isActive).isFalse()
      assertThat(holder.value).isNull()

      cancelAndJoin()
    }
  }

  @Test
  fun `re-acquiring a previously released value, creates a new one`() = runTest {
    launch {
      val holderScope = this.childScope("holder")
      val holder = AcquirableScopedValueOwner(holderScope) { CancellableData(this) }

      val host1 = childScope("host1")
      val host2 = childScope("host2")

      val v1 = holder.acquireValue(host1)
      host1.cancelAndJoin()
      val v2 = holder.acquireValue(host2)

      assertThat(v1).isNotSameAs(v2)
      assertThat(v1.cs.isActive).isFalse()
      assertThat(v2.cs.isActive).isTrue()

      cancelAndJoin()
    }
  }

  @Test
  fun `cancelling the holder scope releases the value`() = runTest {
    launch {
      val holderScope = this.childScope("holder")
      val holder = AcquirableScopedValueOwner(holderScope) { CancellableData(this) }

      val host1 = childScope("host1")

      val v1 = holder.acquireValue(host1)
      holderScope.cancelAndJoin()

      assertThat(v1.cs.isActive).isFalse()
      assertThat(holder.value).isNull()

      cancelAndJoin()
    }
  }

  @Test
  fun `cancelling the holder scope makes it an error to acquireValue`() = runTest {
    launch {
      val holderScope = this.childScope("holder")
      val holder = AcquirableScopedValueOwner(holderScope) { CancellableData(this) }

      holderScope.cancelAndJoin()

      assertThrows<Throwable> { holder.acquireValue(this) }

      cancelAndJoin()
    }
  }
}