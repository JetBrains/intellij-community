// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.actions.branch

/**
 * A marker interface for branch actions placed in "Git.Branch" group that are created dynamically and need to be
 * wrapped in a [GitBranchActionWrapper] to provide proper data context.
 */
interface GitBranchActionToBeWrapped
