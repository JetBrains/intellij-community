// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import org.jetbrains.annotations.CalledInAwt

internal class GHPRDataLoaderImpl(private val dataProviderFactory: (GHPRIdentifier) -> GHPRDataProvider)
  : GHPRDataLoader {

  private var isDisposed = false

  private val cache = mutableMapOf<GHPRIdentifier, DisposalCountingHolder>()

  @CalledInAwt
  override fun getDataProvider(id: GHPRIdentifier, disposable: Disposable): GHPRDataProvider {
    if (isDisposed) throw IllegalStateException("Already disposed")

    return cache.getOrPut(id) {
      DisposalCountingHolder(dataProviderFactory(id)).also {
        Disposer.register(it, Disposable { cache.remove(id) })
      }
    }.acquire(disposable)
  }

  @CalledInAwt
  override fun findDataProvider(id: GHPRIdentifier): GHPRDataProvider? = cache[id]?.provider

  override fun dispose() {
    isDisposed = true
    cache.values.toList().forEach(Disposer::dispose)
  }

  private class DisposalCountingHolder(val provider: GHPRDataProvider) : Disposable {

    private var disposalCounter = 0

    fun acquire(disposable: Disposable): GHPRDataProvider {
      disposalCounter++
      Disposer.register(disposable, Disposable {
        disposalCounter--
        if (disposalCounter <= 0) {
          Disposer.dispose(this)
        }
      })
      return provider
    }

    override fun dispose() {
    }
  }
}