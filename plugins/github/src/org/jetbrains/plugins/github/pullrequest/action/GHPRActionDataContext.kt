// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.action

import com.intellij.openapi.editor.Document
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider
import org.jetbrains.plugins.github.util.GitRemoteUrlCoordinates

interface GHPRActionDataContext {

  val gitRepositoryCoordinates: GitRemoteUrlCoordinates

  val pullRequest: GHPRIdentifier
  val pullRequestDataProvider: GHPRDataProvider

  val submitReviewCommentDocument: Document
}