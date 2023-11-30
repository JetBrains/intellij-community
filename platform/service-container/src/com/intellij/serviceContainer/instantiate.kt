// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.serviceContainer

import com.intellij.diagnostic.PluginException
import com.intellij.openapi.extensions.PluginId
import com.intellij.platform.instanceContainer.instantiation.DependencyResolver
import com.intellij.platform.instanceContainer.instantiation.instantiate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope

internal suspend fun <T> instantiateWithContainer(
  resolver: DependencyResolver,
  parentScope: CoroutineScope,
  instanceClass: Class<T>,
  pluginId: PluginId,
): T {
  try {
    return instantiate(resolver, parentScope, instanceClass)
  }
  catch (ce: CancellationException) {
    throw ce
  }
  catch (t: Throwable) {
    throw PluginException(t, pluginId)
  }
}
