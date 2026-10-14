// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.osc8

import com.intellij.diagnostic.PluginException
import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import org.jetbrains.annotations.ApiStatus

/**
 * Asks the [resolvers] in order and returns the first produced hyperlink.
 */
@ApiStatus.Internal
class CompositeOsc8UriResolver(
  private val resolvers: List<Osc8UriResolver>
) : Osc8UriResolver {
  override fun resolve(uri: String): HyperlinkInfo? {
    return resolvers.firstNotNullOfOrNull {
      it.resolveSafely(uri)
    }
  }
}

private fun Osc8UriResolver.resolveSafely(uri: String): HyperlinkInfo? {
  try {
    return resolve(uri)
  }
  catch (e: Throwable) {
    rethrowControlFlowException(e)
    PluginException.logPluginError(LOG, "Error while applying ${javaClass.name} to '$uri'", e, javaClass)
    return null
  }
}

private val LOG: Logger = logger<CompositeOsc8UriResolver>()
