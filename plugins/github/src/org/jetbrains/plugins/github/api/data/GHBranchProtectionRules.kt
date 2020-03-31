// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data

class GHBranchProtectionRules(val requiredStatusChecks: RequiredStatusChecks?,
                              val enforceAdmins: EnforceAdmins?,
                              val requiredPullRequestReviews: RequiredPullRequestReviews?,
                              val restrictions: Restrictions?) {

  class RequiredStatusChecks(val strict: Boolean, val contexts: List<String>)

  class EnforceAdmins(val enabled: Boolean)

  class RequiredPullRequestReviews(val requiredApprovingReviewCount: Int)

  class Restrictions(val users: List<UserLogin>?, val teams: List<TeamSlug>?)

  class UserLogin(val login: String)

  class TeamSlug(val slug: String)
}
