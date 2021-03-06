// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data

import com.intellij.openapi.util.NlsSafe
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestRequestedReviewer

class GHUser(id: String,
             @NlsSafe override val login: String,
             override val url: String,
             override val avatarUrl: String,
             @NlsSafe override val name: String?)
  : GHNode(id), GHActor, GHPullRequestRequestedReviewer {
  override val shortName: String = login
}
