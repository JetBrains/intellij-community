// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.impl

import java.util.*

/**
 * Defines how consistency checks after modifications of [com.intellij.workspaceModel.storage.MutableEntityStorage]
 * will be performed.
 */
enum class ConsistencyCheckingMode {
  DISABLED,
  ASYNCHRONOUS,
  SYNCHRONOUS;

  companion object {
    val current by lazy {
      val serviceLoader = ServiceLoader.load(ConsistencyCheckingModeProvider::class.java, ConsistencyCheckingModeProvider::class.java.classLoader)
      serviceLoader.map { it.mode }.maxOrNull() ?: DISABLED
    }
  }
}

/**
 * Register implementation of this class in META-INF/services/com.intellij.workspaceModel.storage.impl.ConsistencyCheckingModeProvider.
 * The most strict mode from available providers will be used.
 */
interface ConsistencyCheckingModeProvider {
  val mode: ConsistencyCheckingMode
}