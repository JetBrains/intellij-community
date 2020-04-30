// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.openapi.Disposable
import org.jetbrains.annotations.CalledInAwt
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequest
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider

internal interface GHPRDataProviderRepository : Disposable {
  @CalledInAwt
  fun getDataProvider(id: GHPRIdentifier, disposable: Disposable): GHPRDataProvider

  @CalledInAwt
  fun findDataProvider(id: GHPRIdentifier): GHPRDataProvider?

  @CalledInAwt
  fun addDetailsLoadedListener(disposable: Disposable, listener: (GHPullRequest) -> Unit)
}