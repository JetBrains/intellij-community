// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.action

import com.intellij.openapi.editor.EditorFactory
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider

class GHPRFixedActionDataContext internal constructor(dataContext: GHPRDataContext,
                                                      override val pullRequest: GHPRIdentifier,
                                                      override val pullRequestDataProvider: GHPRDataProvider)
  : GHPRActionDataContext {

  override val gitRepositoryCoordinates = dataContext.gitRepositoryCoordinates

  override val submitReviewCommentDocument by lazy(LazyThreadSafetyMode.NONE) { EditorFactory.getInstance().createDocument("") }
}