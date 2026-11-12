// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.util

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@ApiStatus.Internal
object GitLabThrowableUtil {
  /**
   * Uses [Throwable.getLocalizedMessage], not [Throwable.message]. Some exceptions, such as `HttpStatusErrorException`,
   * override it to return a text from a bundle.
   */
  @Suppress("HardCodedStringLiteral")
  fun localizedMessageOrClassName(e: Throwable): @Nls String =
    e.localizedMessage ?: e::class.simpleName ?: "${e.javaClass.name} occurred"
}