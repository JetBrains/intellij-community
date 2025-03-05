// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api.data

// Values is taken from: https://gitlab.com/gitlab-org/gitlab/blob/5ae4e94690fda1727816bdf7e01dc520d0430547/ee/app/models/ee/plan.rb#L9
enum class GitLabPlan {
  // New tier names
  FREE,
  PREMIUM,
  PREMIUM_TRIAL,
  ULTIMATE,
  ULTIMATE_TRIAL,

  // Old tier names (https://about.gitlab.com/blog/2021/01/26/new-gitlab-product-subscription-model)
  DEFAULT,
  BRONZE,
  SILVER,
  GOLD,

  OPENSOURCE
}