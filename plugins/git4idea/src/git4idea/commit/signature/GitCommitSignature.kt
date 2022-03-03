// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.commit.signature

import com.intellij.openapi.util.NlsSafe
import com.intellij.vcs.log.data.VcsCommitExternalStatus

internal sealed class GitCommitSignature : VcsCommitExternalStatus {
  object NoSignature : GitCommitSignature()

  class Verified(val user: @NlsSafe String, val fingerprint: @NlsSafe String) : GitCommitSignature()

  class NotVerified(val reason: VerificationFailureReason) : GitCommitSignature()

  object Bad : GitCommitSignature()

  enum class VerificationFailureReason {
    UNKNOWN,
    EXPIRED,
    EXPIRED_KEY,
    REVOKED_KEY,
    CANNOT_VERIFY
  }
}