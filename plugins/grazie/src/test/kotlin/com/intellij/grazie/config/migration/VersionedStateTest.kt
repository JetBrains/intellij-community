package com.intellij.grazie.config.migration

import org.junit.Assert
import org.junit.Test

class VersionedStateTest {
  private enum class FstVersion : VersionedState.Version<FstState> {
    FIRST {
      override fun migrate(state: FstState) = state.copy(new = state.old, old = state.new)
    },
    SECOND {
      override fun migrate(state: FstState) = state.copy(new = state.new + 1, old = state.old - 1)
    },
    THIRD;

    override fun next() = values().getOrNull(ordinal + 1)
  }

  private data class FstState(val new: Int, val old: Int, override val version: FstVersion) : VersionedState<FstVersion, FstState> {
    override fun increment() = copy(version = version.next()!!)
  }

  @Test
  fun `test full chain migration`() {
    val old = FstState(new = -1, old = 1, version = FstVersion.FIRST)
    val new = VersionedState.migrate(old)

    Assert.assertEquals(2, new.new)
    Assert.assertEquals(-2, new.old)
    Assert.assertEquals(FstVersion.THIRD, new.version)
  }

  private enum class SndVersion : VersionedState.Version<SndState> {
    FIRST,
    SECOND {
      override fun migrate(state: SndState) = state.copy(new = state.new + 1, old = state.old - 1)
    },
    THIRD;

    override fun next() = values().getOrNull(ordinal + 1)
  }

  private data class SndState(val new: Int, val old: Int, override val version: SndVersion) : VersionedState<SndVersion, SndState> {
    override fun increment() = copy(version = version.next()!!)
  }


  @Test
  fun `test migration with skip`() {
    val old = SndState(new = -1, old = 1, version = SndVersion.FIRST)
    val new = VersionedState.migrate(old)

    Assert.assertEquals(0, new.new)
    Assert.assertEquals(0, new.old)
    Assert.assertEquals(SndVersion.THIRD, new.version)
  }

  private enum class ThirdVersion : VersionedState.Version<ThirdState> {
    FIRST,
    SECOND {
      override fun migrate(state: ThirdState) = state.copy(version = FIRST)
    },
    THIRD;

    override fun next() = values().getOrNull(ordinal + 1)
  }

  private data class ThirdState(val new: Int, val old: Int, override val version: ThirdVersion) : VersionedState<ThirdVersion, ThirdState> {
    override fun increment() = copy(version = version.next()!!)
  }


  @Test
  fun `test version should not be changed during migration`() {

    val old = SndState(new = -1, old = 1, version = SndVersion.FIRST)
    val new = VersionedState.migrate(old)

    Assert.assertEquals(0, new.new)
    Assert.assertEquals(0, new.old)
    Assert.assertEquals(SndVersion.THIRD, new.version)
  }

}