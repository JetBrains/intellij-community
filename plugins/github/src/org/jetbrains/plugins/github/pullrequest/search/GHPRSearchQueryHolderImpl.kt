// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.search

import com.intellij.openapi.Disposable
import com.intellij.util.EventDispatcher
import org.jetbrains.plugins.github.pullrequest.data.GHPRSearchQuery
import org.jetbrains.plugins.github.pullrequest.ui.SimpleEventListener
import org.jetbrains.plugins.github.util.GithubUtil.Delegates.observableField

internal class GHPRSearchQueryHolderImpl : GHPRSearchQueryHolder {

  private val queryChangeEventDispatcher = EventDispatcher.create(SimpleEventListener::class.java)

  override var queryString by observableField("", queryChangeEventDispatcher)

  override var query: GHPRSearchQuery
    get() = GHPRSearchQuery.parseFromString(queryString)
    set(value) {
      queryString = value.toString()
    }

  override fun addQueryChangeListener(disposable: Disposable, listener: () -> Unit) =
    SimpleEventListener.addDisposableListener(queryChangeEventDispatcher, disposable, listener)
}