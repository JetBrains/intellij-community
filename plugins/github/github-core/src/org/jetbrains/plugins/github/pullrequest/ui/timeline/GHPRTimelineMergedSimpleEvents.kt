// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.timeline

import org.jetbrains.plugins.github.api.data.GHLabel
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestRequestedReviewer
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

  private val _addedReviewers = mutableSetOf<GHPullRequestRequestedReviewer>()
  val addedReviewers: Set<GHPullRequestRequestedReviewer> get() = _addedReviewers
  private val _removedReviewers = mutableSetOf<GHPullRequestRequestedReviewer>()
  val removedReviewers: Set<GHPullRequestRequestedReviewer> get() = _removedReviewers

  private var _rename: Pair<String, String>? = null
  val rename: Pair<String, String>? get() = _rename?.let { if (it.first != it.second) it else null }

  override fun addNonMergedEvent(event: GHPRTimelineEvent.Simple) {
    when (event) {
      is GHPRLabeledEvent -> if (!_removedLabels.remove(event.label)) _addedLabels.add(event.label)
      is GHPRUnlabeledEvent -> if (!_addedLabels.remove(event.label)) _removedLabels.add(event.label)

      is GHPRAssignedEvent -> if (!_unassignedPeople.remove(event.user)) _assignedPeople.add(event.user)
      is GHPRUnassignedEvent -> if (!_assignedPeople.remove(event.user)) _unassignedPeople.add(event.user)

      is GHPRReviewRequestedEvent -> {
        val reviewer = event.requestedReviewer
        if (reviewer != null && !_removedReviewers.remove(reviewer)) _addedReviewers.add(reviewer)
      }
      is GHPRReviewUnrequestedEvent -> {
        val reviewer = event.requestedReviewer
        if (reviewer != null && !_addedReviewers.remove(reviewer)) _removedReviewers.add(reviewer)
      }

      is GHPRRenamedTitleEvent -> _rename = (_rename?.first ?: event.previousTitle) to event.currentTitle
    }
  }

  override fun hasAnyChanges(): Boolean =
    assignedPeople.isNotEmpty() || unassignedPeople.isNotEmpty() ||
    addedLabels.isNotEmpty() || removedLabels.isNotEmpty() ||
    addedReviewers.isNotEmpty() || removedReviewers.isNotEmpty() ||
    rename != null
}