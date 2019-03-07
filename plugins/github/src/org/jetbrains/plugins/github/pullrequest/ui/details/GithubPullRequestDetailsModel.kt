// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.details

import com.intellij.openapi.Disposable
import com.intellij.util.EventDispatcher
import org.jetbrains.annotations.CalledInAwt
import org.jetbrains.plugins.github.api.data.GithubPullRequestDetailedWithHtml
import java.util.*
import kotlin.properties.Delegates

class GithubPullRequestDetailsModel {
  private val changeEventDispatcher = EventDispatcher.create(GithubPullRequestDetailsChangedListener::class.java)

  var details: GithubPullRequestDetailedWithHtml?
    by Delegates.observable<GithubPullRequestDetailedWithHtml?>(null) { _, _, _ ->
      changeEventDispatcher.multicaster.detailsChanged()
    }

  @CalledInAwt
  fun addDetailsChangedListener(disposable: Disposable, listener: () -> Unit) =
    changeEventDispatcher.addListener(object : GithubPullRequestDetailsChangedListener {
      override fun detailsChanged() {
        listener()
      }
    }, disposable)


  private interface GithubPullRequestDetailsChangedListener : EventListener {
    fun detailsChanged()
  }
}