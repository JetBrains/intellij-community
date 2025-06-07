// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.api.data.pullrequest

import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier

data class GHPullRequestRestIdOnly(val id: String,
                                   val nodeId: String,
                                   val number: Long)

fun GHPullRequestRestIdOnly.toPRIdentifier(): GHPRIdentifier = GHPRIdentifier(nodeId, number)
