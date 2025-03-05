// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.action

import com.intellij.openapi.ide.CopyPasteManager
import java.awt.datatransfer.StringSelection

class GHPullRequestCopyURLAction : GHPullRequestURLAction() {
  override fun handleURL(pullRequestUrl: String) = CopyPasteManager.getInstance().setContents(StringSelection(pullRequestUrl))
}