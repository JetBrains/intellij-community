// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.components

import com.intellij.openapi.components.impl.stores.IComponentStore
import kotlinx.coroutines.Deferred
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface ComponentManagerEx {
  @ApiStatus.Experimental
  @ApiStatus.Internal
  suspend fun <T : Any> getServiceAsync(keyClass: Class<T>): Deferred<T> {
    throw AbstractMethodError()
  }

  val componentStore: IComponentStore
}