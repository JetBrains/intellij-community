// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap


@ApiStatus.Internal
class WriteActionPresenceServiceImpl : WriteActionPresenceService {
  private val writeActions = ConcurrentHashMap<Class<*>, Unit>()

  override fun addWriteActionClass(clazz: Class<*>): Boolean {
    val hasClass = writeActions.containsKey(clazz)
    writeActions[clazz] = Unit
    return !hasClass
  }

  override fun removeWriteActionClass(clazz: Class<*>) {
    writeActions.remove(clazz)
  }

  override fun hasWriteAction(clazz: Class<*>): Boolean {
    return writeActions.containsKey(clazz)
  }
}