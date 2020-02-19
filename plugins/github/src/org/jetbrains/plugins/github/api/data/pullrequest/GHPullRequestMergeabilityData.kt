// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data.pullrequest

class GHPullRequestMergeabilityData(val url: String,
                                    val number: Long,
                                    val mergeable: GHPullRequestMergeableState,
                                    val canBeRebased: Boolean,
                                    val headRefOid: String)
