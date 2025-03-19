// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.actions.tag

import com.intellij.openapi.util.NlsActions
import git4idea.GitTag
import git4idea.actions.ref.GitSingleRefAction
import java.util.function.Supplier

internal abstract class GitSingleTagAction(dynamicText: Supplier<@NlsActions.ActionText String>) : GitSingleRefAction<GitTag>(dynamicText) {
  override val refClass = GitTag::class
}