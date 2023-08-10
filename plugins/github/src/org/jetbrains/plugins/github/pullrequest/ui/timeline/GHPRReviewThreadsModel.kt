// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.timeline

import com.intellij.ui.CollectionListModel
import com.intellij.util.containers.SortedList
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewThread
import org.jetbrains.plugins.github.pullrequest.comment.ui.GHPRReviewThreadModel
import org.jetbrains.plugins.github.pullrequest.comment.ui.GHPRReviewThreadModelImpl

class GHPRReviewThreadsModel
  : CollectionListModel<GHPRReviewThreadModel>(SortedList(compareBy { it.createdAt }), true) {

  var loaded = false
    private set

  fun update(list: List<GHPullRequestReviewThread>) {
    loaded = true
    //easier then creating another event type and doesn't break JList
    fireContentsChanged(this, -1, -1)

    val threadsById = list.associateBy { it.id }.toMutableMap()
    val removals = mutableListOf<GHPRReviewThreadModel>()
    for (item in items) {
      val thread = threadsById.remove(item.id)
      if (thread != null) item.update(thread)
      else removals.add(item)
    }
    for (model in removals) {
      remove(model)
    }
    for (thread in threadsById.values) {
      add(GHPRReviewThreadModelImpl(thread))
    }
  }
}