// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.common

open class CodexAppServerException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class CodexCliNotFoundException : CodexAppServerException("Codex CLI not found")
