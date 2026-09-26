// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.remote.hosting.ui

import com.intellij.collaboration.messages.CollaborationToolsBundle
import git4idea.remote.hosting.HostedGitRepositoryRemote
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

/**
 * Represents a request to resolve conflicts in a code review locally.
 */
@ApiStatus.Experimental
class ResolveConflictsLocallyCoordinates(
  val headRemoteDescriptor: HostedGitRepositoryRemote,
  val headRefName: String,
  val baseRemoteDescriptor: HostedGitRepositoryRemote,
  val baseRefName: String
)

@ApiStatus.Experimental
enum class BaseOrHead(val text: @Nls String) {
  Base(CollaborationToolsBundle.message("review.details.resolve-conflicts.base")),
  Head(CollaborationToolsBundle.message("review.details.resolve-conflicts.head"));
}
