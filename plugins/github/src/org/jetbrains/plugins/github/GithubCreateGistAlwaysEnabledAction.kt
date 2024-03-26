// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github

import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent

/**
 * A wrapper around [GithubCreateGistAction].
 * This action can be used in toolbars and context menus, and it won't be disabled or hidden when the user
 * does not have a GitHub account connected. If this behavior is desired, use this action. Otherwise, use
 * [GithubCreateGistAction].
 *
 * This action won't show up in search, otherwise it would be possible to have both this action and
 * [GithubCreateGistAction] in the search results.
 */
class GithubCreateGistAlwaysEnabledAction : GithubCreateGistAction() {
  override fun update(e: AnActionEvent) {
    // We're intentionally not calling super.update(e), as it would check for a connected GitHub account,
    //  and we don't want that.

    /** We're hiding this action from search so that we won't show it alongside [GithubCreateGistAction] */
    e.presentation.isEnabledAndVisible = !ActionPlaces.isMainMenuOrActionSearch(e.place)
  }
}
