// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data.pullrequest

import org.jetbrains.plugins.github.api.data.GHCommitWithCheckStatuses
import org.jetbrains.plugins.github.api.data.GHNode

class GHPullRequestCommitWithCheckStatuses(id: String, val commit: GHCommitWithCheckStatuses) : GHNode(id)