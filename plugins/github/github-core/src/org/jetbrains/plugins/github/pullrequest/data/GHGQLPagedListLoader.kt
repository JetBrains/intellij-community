// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.plugins.github.api.util.SimpleGHGQLPagesLoader

internal class GHGQLPagedListLoader<T>(
  parentCs: CoroutineScope,
  progressManager: ProgressManager,
  private val loader: SimpleGHGQLPagesLoader<T>,
) : GHListLoaderBase<T>(parentCs, progressManager) {

  override fun canLoadMore() = !loading && (loader.hasNext || error != null)

  override fun doLoadMore(indicator: ProgressIndicator, update: Boolean) = loader.loadNext(indicator, update)

  override fun reset() {
    loader.reset()
    super.reset()
  }
}
