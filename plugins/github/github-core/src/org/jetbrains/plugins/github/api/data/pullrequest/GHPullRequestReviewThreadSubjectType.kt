// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.api.data.pullrequest

enum class GHPullRequestReviewThreadSubjectType {
  //A comment that has been made against the file of a pull request
  FILE,

  //A comment that has been made against the line of a pull request
  LINE
}
