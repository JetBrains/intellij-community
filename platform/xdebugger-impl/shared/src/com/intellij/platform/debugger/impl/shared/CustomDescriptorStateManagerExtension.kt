// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.shared

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.xdebugger.frame.XDescriptor
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus

/**
 * Creates a plugin-specific state for a given [XDescriptor].
 * For example, if a [XDescriptor] contains a flow, this extension should create an object which will keep the flow's state.
 * Later it can be accessed with [CustomDescriptorStateManager].
 */
@ApiStatus.Internal
interface CustomDescriptorStateManagerExtension {
  fun createState(descriptor: XDescriptor, cs: CoroutineScope): Any?

  companion object {
    private val EP_NAME = ExtensionPointName.create<CustomDescriptorStateManagerExtension>("com.intellij.xdebugger.descriptorStateManager")

    fun createState(descriptor: XDescriptor, cs: CoroutineScope): Any? {
      return EP_NAME.computeSafeIfAny { it.createState(descriptor, cs) }
    }
  }
}
