// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application

import com.intellij.ide.CliResult
import com.intellij.ide.ProtocolHandler

internal class JBProtocolHandler : ProtocolHandler {
  override val scheme: String
    get() = JBProtocolCommand.SCHEME

  override suspend fun process(query: String): CliResult {
    return JBProtocolCommand.execute(query)
  }
}
