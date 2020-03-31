// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data.pullrequest.timeline

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import org.jetbrains.plugins.github.api.data.GHIssueComment
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestCommit
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReview
import org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRTimelineItem.Unknown

/*REQUIRED
IssueComment
PullRequestCommit (Commit in GHE)
PullRequestReview

RenamedTitleEvent
ClosedEvent | ReopenedEvent | MergedEvent
AssignedEvent | UnassignedEvent
LabeledEvent | UnlabeledEvent
ReviewRequestedEvent | ReviewRequestRemovedEvent
ReviewDismissedEvent

BaseRefChangedEvent | BaseRefForcePushedEvent
HeadRefDeletedEvent | HeadRefForcePushedEvent | HeadRefRestoredEvent
*/
/*MAYBE
LockedEvent | UnlockedEvent

CommentDeletedEvent
???PullRequestCommitCommentThread
???PullRequestReviewThread
AddedToProjectEvent
ConvertedNoteToIssueEvent
RemovedFromProjectEvent
MovedColumnsInProjectEvent

TransferredEvent
UserBlockedEvent

PullRequestRevisionMarker

DeployedEvent
DeploymentEnvironmentChangedEvent
PullRequestReviewThread
PinnedEvent | UnpinnedEvent
SubscribedEvent | UnsubscribedEvent
MilestonedEvent | DemilestonedEvent
MentionedEvent | ReferencedEvent | CrossReferencedEvent
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "__typename", visible = false,
              defaultImpl = Unknown::class)
@JsonSubTypes(
  JsonSubTypes.Type(name = "IssueComment", value = GHIssueComment::class),
  JsonSubTypes.Type(name = "PullRequestCommit", value = GHPullRequestCommit::class),
  JsonSubTypes.Type(name = "PullRequestReview", value = GHPullRequestReview::class),

  JsonSubTypes.Type(name = "ReviewDismissedEvent", value = GHPRReviewDismissedEvent::class),

  JsonSubTypes.Type(name = "RenamedTitleEvent", value = GHPRRenamedTitleEvent::class),

  JsonSubTypes.Type(name = "ClosedEvent", value = GHPRClosedEvent::class),
  JsonSubTypes.Type(name = "ReopenedEvent", value = GHPRReopenedEvent::class),
  JsonSubTypes.Type(name = "MergedEvent", value = GHPRMergedEvent::class),

  JsonSubTypes.Type(name = "AssignedEvent", value = GHPRAssignedEvent::class),
  JsonSubTypes.Type(name = "UnassignedEvent", value = GHPRUnassignedEvent::class),

  JsonSubTypes.Type(name = "LabeledEvent", value = GHPRLabeledEvent::class),
  JsonSubTypes.Type(name = "UnlabeledEvent", value = GHPRUnlabeledEvent::class),

  JsonSubTypes.Type(name = "ReviewRequestedEvent", value = GHPRReviewRequestedEvent::class),
  JsonSubTypes.Type(name = "ReviewRequestRemovedEvent", value = GHPRReviewUnrequestedEvent::class),

  JsonSubTypes.Type(name = "BaseRefChangedEvent", value = GHPRBaseRefChangedEvent::class),
  JsonSubTypes.Type(name = "BaseRefForcePushedEvent", value = GHPRBaseRefForcePushedEvent::class),

  JsonSubTypes.Type(name = "HeadRefDeletedEvent", value = GHPRHeadRefDeletedEvent::class),
  JsonSubTypes.Type(name = "HeadRefForcePushedEvent", value = GHPRHeadRefForcePushedEvent::class),
  JsonSubTypes.Type(name = "HeadRefRestoredEvent", value = GHPRHeadRefRestoredEvent::class)
)
interface GHPRTimelineItem {
  class Unknown : GHPRTimelineItem
}