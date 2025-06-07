// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.psi

import com.intellij.platform.syntax.Logger
import com.intellij.openapi.diagnostic.Logger as IJLogger
import com.intellij.platform.syntax.util.log.LogProvider

internal class IntelliJLogProvider : LogProvider {
  override fun getLogger(name: String): Logger =
    IJLogger.getInstance(name).asSyntaxLogger()
}
