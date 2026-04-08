// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.common.extensions

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionPointName
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private sealed interface OverrideState<out T> {
  data object Unset : OverrideState<Nothing>

  data class Value<T>(val value: T) : OverrideState<T>
}

class OverridableValue<T>(
  private val fallback: () -> T,
) {
  private val lock = Any()

  @Volatile
  private var overrideState: OverrideState<T> = OverrideState.Unset

  fun value(): T {
    return when (val current = overrideState) {
      OverrideState.Unset -> fallback()
      is OverrideState.Value -> current.value
    }
  }

  fun <R> withOverride(value: T, action: () -> R): R {
    return synchronized(lock) {
      val previous = overrideState
      overrideState = OverrideState.Value(value)
      try {
        action()
      }
      finally {
        overrideState = previous
      }
    }
  }
}

class SuspendingOverridableValue<T>(
  private val fallback: () -> T,
) {
  private val mutex = Mutex()

  @Volatile
  private var overrideState: OverrideState<T> = OverrideState.Unset

  fun value(): T {
    return when (val current = overrideState) {
      OverrideState.Unset -> fallback()
      is OverrideState.Value -> current.value
    }
  }

  suspend fun <R> withOverride(value: T, action: suspend () -> R): R {
    return mutex.withLock {
      val previous = overrideState
      overrideState = OverrideState.Value(value)
      try {
        action()
      }
      finally {
        overrideState = previous
      }
    }
  }
}

class SnapshotExtensionPointCache<T : Any, S : Any>(
  private val log: Logger,
  private val extensionPoint: ExtensionPointName<T>,
  private val cacheId: Class<S>,
  private val emptySnapshot: S,
  private val unavailableMessage: String,
  private val buildSnapshot: (Iterable<T>) -> S,
) {
  fun getSnapshotOrEmpty(): S {
    return try {
      extensionPoint.computeIfAbsent(cacheId) {
        buildSnapshot(extensionPoint.extensionList)
      }
    }
    catch (t: IllegalStateException) {
      log.debug(unavailableMessage, t)
      emptySnapshot
    }
    catch (t: IllegalArgumentException) {
      log.debug(unavailableMessage, t)
      emptySnapshot
    }
  }
}

class SingleExtensionPointResolver<T : Any>(
  private val log: Logger,
  private val extensionPoint: ExtensionPointName<T>,
  private val unavailableMessage: String,
  private val multipleExtensionsMessage: (List<T>) -> String,
) {
  fun findFirstOrNull(): T? {
    val extensions = try {
      extensionPoint.extensionList
    }
    catch (t: IllegalStateException) {
      log.debug(unavailableMessage, t)
      return null
    }
    catch (t: IllegalArgumentException) {
      log.debug(unavailableMessage, t)
      return null
    }

    if (extensions.size > 1) {
      log.warn(multipleExtensionsMessage(extensions))
    }
    return extensions.firstOrNull()
  }
}
