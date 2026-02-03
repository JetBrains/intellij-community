// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.fetch

import com.intellij.openapi.util.NlsSafe
import git4idea.i18n.GitBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey

enum class GitFetchTagsMode(
  private val modeNameBundleKey: @PropertyKey(resourceBundle = GitBundle.BUNDLE) String,
  private val descriptionBundleKey: @PropertyKey(resourceBundle = GitBundle.BUNDLE) String?,
  val param: @NlsSafe String?,
) {
  DEFAULT("settings.git.fetch.tag.mode.default", "settings.git.fetch.tag.mode.default.description", null),

  @Suppress("unused")
  PRUNE_TAGS("settings.git.fetch.tag.mode.prune", null, "--prune-tags"),

  @Suppress("unused")
  ALL_TAGS("settings.git.fetch.tag.mode.always", null, "--tags"),

  @Suppress("unused")
  NO_TAGS("settings.git.fetch.tag.mode.never", null, "--no-tags");

  fun getModeName(): @Nls String = GitBundle.message(modeNameBundleKey)

  fun getDescription(): @Nls String {
    if (descriptionBundleKey == null) return param ?: ""
    return GitBundle.message(descriptionBundleKey)
  }
}