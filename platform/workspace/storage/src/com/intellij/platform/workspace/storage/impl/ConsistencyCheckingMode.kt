// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform.workspace.storage.impl

import java.util.*

/**
 * Defines how consistency checks after modifications of [com.intellij.platform.workspace.storage.MutableEntityStorage]
 * will be performed.
 */
public enum class ConsistencyCheckingMode {
  DISABLED,
  ASYNCHRONOUS,
  SYNCHRONOUS;

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