// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.action

import com.intellij.ide.BrowserUtil

class GHPullRequestOpenURLAction : GHPullRequestURLAction() {
  override fun handleURL(pullRequestUrl: String) = BrowserUtil.open(pullRequestUrl)
}