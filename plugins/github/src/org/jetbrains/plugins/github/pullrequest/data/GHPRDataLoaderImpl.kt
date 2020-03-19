// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import com.google.common.cache.CacheBuilder
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runInEdt
import com.intellij.util.EventDispatcher
import org.jetbrains.annotations.CalledInAwt
import java.util.*

internal class GHPRDataLoaderImpl(private val dataProviderFactory: (GHPRIdentifier) -> GHPRDataProvider)
  : GHPRDataLoader {

  private var isDisposed = false
  private val cache = CacheBuilder.newBuilder()
    .removalListener<GHPRIdentifier, GHPRDataProvider> {
      runInEdt { invalidationEventDispatcher.multicaster.providerChanged(it.key) }
    }
    .maximumSize(5)
    .build<GHPRIdentifier, GHPRDataProvider>()

  private val invalidationEventDispatcher = EventDispatcher.create(DataInvalidatedListener::class.java)

  @CalledInAwt
  override fun invalidateAllData() {
    cache.invalidateAll()
  }

  @CalledInAwt
  override fun getDataProvider(id: GHPRIdentifier): GHPRDataProvider {
    if (isDisposed) throw IllegalStateException("Already disposed")

    return cache.get(id) {
      dataProviderFactory(id)
    }
  }

  @CalledInAwt
  override fun findDataProvider(id: GHPRIdentifier): GHPRDataProvider? = cache.getIfPresent(id)

  override fun addInvalidationListener(disposable: Disposable, listener: (GHPRIdentifier) -> Unit) =
    invalidationEventDispatcher.addListener(object : DataInvalidatedListener {
      override fun providerChanged(id: GHPRIdentifier) {
        listener(id)
      }
    }, disposable)

  override fun dispose() {
    invalidateAllData()
    isDisposed = true
  }

  private interface DataInvalidatedListener : EventListener {
    fun providerChanged(id: GHPRIdentifier)
  }
}