// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("VirtualThreads")
@file:ApiStatus.Experimental

package com.intellij.virtualThreads

import org.jetbrains.annotations.ApiStatus

/**
 * Creates a new [virtual thread][java.lang.Thread.Builder.OfVirtual] that runs the specified [block] of code.
 *
 * This function is opposed to [kotlin.concurrent.thread], which creates a new *platform* thread
 */
fun virtualThread(
  start: Boolean = true,
  name: String? = null,
  contextClassLoader: ClassLoader? = null,
  block: () -> Unit,
): Thread {
  val thread = IntelliJVirtualThreads.ofVirtual().apply {
    if (name != null) {
      name(name)
    }
  }.unstarted(block)
  if (contextClassLoader != null) {
    thread.contextClassLoader = contextClassLoader
  }
  return if (start) {
    thread.apply { start() }
  } else {
    thread
  }
}
