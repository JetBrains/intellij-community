// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.timeline

import org.jetbrains.plugins.github.api.data.GHLabel
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewer
import org.jetbrains.plugins.github.api.data.pullrequest.timeline.*

class GHPRTimelineMergedSimpleEvents : GHPRTimelineMergedEvents<GHPRTimelineEvent.Simple>(), GHPRTimelineEvent.Simple {

  private val _addedLabels = mutableSetOf<GHLabel>()
  val addedLabels: Set<GHLabel> get() = _addedLabels
  private val _removedLabels = mutableSetOf<GHLabel>()
  val removedLabels: Set<GHLabel> get() = _removedLabels

  private val _assignedPeople = mutableSetOf<GHUser>()
  val assignedPeople: Set<GHUser> get() = _assignedPeople
  private val _unassignedPeople = mutableSetOf<GHUser>()
  val unassignedPeople: Set<GHUser> get() = _unassignedPeople

  private val _addedReviewers = mutableSetOf<GHPullRequestReviewer>()
  val addedReviewers: Set<GHPullRequestReviewer> get() = _addedReviewers
  private val _removedReviewers = mutableSetOf<GHPullRequestReviewer>()
  val removedReviewers: Set<GHPullRequestReviewer> get() = _removedReviewers

  private var _rename: Pair<String, String>? = null
  val rename: Pair<String, String>? get() = _rename?.let { if (it.first != it.second) it else null }

  override fun addNonMergedEvent(event: GHPRTimelineEvent.Simple) {
    when (event) {
      is GHPRLabeledEvent -> if (!_removedLabels.remove(event.label)) _addedLabels.add(event.label)
      is GHPRUnlabeledEvent -> if (!_addedLabels.remove(event.label)) _removedLabels.add(event.label)

      is GHPRAssignedEvent -> if (!_unassignedPeople.remove(event.user)) _assignedPeople.add(event.user)
      is GHPRUnassignedEvent -> if (!_assignedPeople.remove(event.user)) _unassignedPeople.add(event.user)

      is GHPRReviewRequestedEvent -> if (!_removedReviewers.remove(event.requestedReviewer)) _addedReviewers.add(event.requestedReviewer)
      is GHPRReviewUnrequestedEvent -> if (!_addedReviewers.remove(event.requestedReviewer)) _removedReviewers.add(event.requestedReviewer)

      is GHPRRenamedTitleEvent -> _rename = (_rename?.first ?: event.previousTitle) to event.currentTitle
    }
  }

  override fun hasAnyChanges(): Boolean =
    assignedPeople.isNotEmpty() || unassignedPeople.isNotEmpty() ||
    addedLabels.isNotEmpty() || removedLabels.isNotEmpty() ||
    addedReviewers.isNotEmpty() || removedReviewers.isNotEmpty() ||
    rename != null
}