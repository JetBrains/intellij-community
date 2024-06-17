// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.impl

import java.util.*

/**
 * Defines how consistency checks after modifications of [com.intellij.platform.workspace.storage.MutableEntityStorage]
 * will be performed.
 */
public enum class ConsistencyCheckingMode {
  DISABLED,
  ENABLED;

  internal companion object {
    internal val current by lazy {
      val serviceLoader = ServiceLoader.load(ConsistencyCheckingModeProvider::class.java, ConsistencyCheckingModeProvider::class.java.classLoader)
      serviceLoader.maxOfOrNull { it.mode } ?: DISABLED
    }
  }
}

/**
 * Register implementation of this class in META-INF/services/com.intellij.platform.workspace.storage.impl.ConsistencyCheckingModeProvider.
 * The strictest mode from available providers will be used.
 */
public interface ConsistencyCheckingModeProvider {
  public val mode: ConsistencyCheckingMode
}

/** Only for limited usages in the platform */
public object ConsistencyCheckingDisabler {
  public val forceDisableConsistencyCheck: ThreadLocal<Boolean> = ThreadLocal<Boolean>().also { it.set(false) }

  public inline fun <T> withDisabled(action: () -> T): T {
    forceDisableConsistencyCheck.set(true)
    try {
      return action()
    }
    finally {
      forceDisableConsistencyCheck.set(false)
    }
  }

  internal fun isDisabled(): Boolean = forceDisableConsistencyCheck.get() ?: false
}
