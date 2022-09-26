// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.components

import com.intellij.openapi.components.impl.stores.IComponentStore
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface ComponentStoreOwner {
  val componentStore: IComponentStore
}