// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.action

import com.intellij.openapi.ide.CopyPasteManager
import java.awt.datatransfer.StringSelection

class GitLabMergeRequestCopyURLAction : GitLabMergeRequestURLAction() {
  override fun handleURL(mergeRequestUrl: String) = CopyPasteManager.getInstance().setContents(StringSelection(mergeRequestUrl))
}