// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

/**
 * [com.intellij.openapi.roots.impl.DirectoryIndexExcludePolicy] is not incremental, so don't add new ones in intellij monorepo sources.
 * Describe the excluded files with a workspace entity and a `WorkspaceFileIndexContributor` instead.
 */
val existingDirectoryIndexExcludePoliciesInCommunity: Set<String> = setOf(
  "com.android.tools.idea.gradle.project.importing.InitialImportExcludeDirectoryPolicy",
)
