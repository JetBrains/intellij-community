// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.search

import com.intellij.openapi.Disposable
import org.jetbrains.annotations.CalledInAwt
import org.jetbrains.plugins.github.pullrequest.data.GHPRSearchQuery

internal interface GHPRSearchQueryHolder {

  @get:CalledInAwt
  @set:CalledInAwt
  var queryString: String

  @get:CalledInAwt
  @set:CalledInAwt
  var query: GHPRSearchQuery

  fun addQueryChangeListener(disposable: Disposable, listener: () -> Unit)
}