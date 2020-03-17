// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer

class GHPRCountingTimelineLoaderHolder(private val loaderFactory: () -> GHPRTimelineLoader)
  : GHPRTimelineLoaderHolder {

  private var loader: GHPRTimelineLoader? = null
  private var loaderDisposable: Disposable? = null
  private var disposalCounter = 0

  override val timelineLoader: GHPRTimelineLoader?
    get() = loader

  override fun acquireTimelineLoader(disposable: Disposable): GHPRTimelineLoader {
    disposalCounter++
    if (loader == null) {
      loader = loaderFactory()
      loaderDisposable = Disposer.newDisposable("Timeline loader disposable")
      Disposer.register(loaderDisposable!!, loader!!)
    }
    Disposer.register(disposable, Disposable {
      disposalCounter--
      if (disposalCounter <= 0) {
        loader = null
        Disposer.dispose(loaderDisposable!!)
        loaderDisposable = null
      }
    })
    return loader!!
  }
}