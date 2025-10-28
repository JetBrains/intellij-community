// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui

import com.intellij.collaboration.ui.html.AsyncHtmlImageLoader
import org.jetbrains.annotations.ApiStatus

/**
 * Class which handles external files loading
 */
@ApiStatus.Internal
class GitLabContextDataLoader(
  val imageLoader: AsyncHtmlImageLoader,
  val uploadFileUrlBase: String
)