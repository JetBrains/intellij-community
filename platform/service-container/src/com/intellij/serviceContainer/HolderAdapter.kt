// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.serviceContainer

import com.intellij.platform.instanceContainer.internal.InstanceHolder
import org.picocontainer.ComponentAdapter

internal class HolderAdapter(
  private val key: Any,
  val holder: InstanceHolder,
) : ComponentAdapter {

  override fun getComponentKey(): Any {
    return key
  }

  override fun getComponentImplementation(): Class<*> {
    return holder.instanceClass()
  }

  override fun getComponentInstance(): Any {
    return checkNotNull(holder.getOrCreateInstanceBlocking(key.toString(), keyClass = null))
  }
}
