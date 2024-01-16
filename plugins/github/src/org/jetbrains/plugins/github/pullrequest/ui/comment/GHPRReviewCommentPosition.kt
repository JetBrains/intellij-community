// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.comment

import com.intellij.collaboration.util.RefComparisonChange

data class GHPRReviewCommentPosition(val change: RefComparisonChange, val isCumulative: Boolean, val location: GHPRReviewCommentLocation)