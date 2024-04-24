// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.resources.providers

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.CheckedDisposable
import com.intellij.openapi.util.Disposer
import org.jetbrains.annotations.TestOnly
import kotlin.reflect.KClass

/**
 * See [Disposable]
 */
@TestOnly
class DisposableProvider : ResourceProvider<Disposable> {
  override val resourceType: KClass<Disposable> = Disposable::class
  override suspend fun create(storage: ResourceStorage): Disposable = Disposer.newCheckedDisposable()

  override val needsApplication: Boolean = false

  override suspend fun destroy(resource: Disposable) {
    resource as CheckedDisposable
    assert(!resource.isDisposed) {
      "$resource already disposed"
    }
    Disposer.dispose(resource)
  }
}