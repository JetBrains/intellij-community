// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.serviceContainer

import com.intellij.configurationStore.NonPersistentStore
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.openapi.application.Application
import com.intellij.openapi.components.impl.stores.IComponentStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import org.jetbrains.annotations.TestOnly
import kotlin.coroutines.EmptyCoroutineContext

@OptIn(DelicateCoroutinesApi::class)
@TestOnly
class TestComponentManager(
  override var isGetComponentAdapterOfTypeCheckEnabled: Boolean = true,
  @JvmField val parentScope: CoroutineScope = GlobalScope,
) : ComponentManagerImpl(parent = null, parentScope = parentScope, additionalContext = EmptyCoroutineContext) {
  override val componentStore: IComponentStore
    get() = NonPersistentStore

  override fun getContainerDescriptor(pluginDescriptor: IdeaPluginDescriptorImpl) = pluginDescriptor.appContainerDescriptor

  override fun getApplication(): Application? = null
}