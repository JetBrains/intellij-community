// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.interpreter

import com.intellij.cce.actions.*
import com.intellij.cce.core.Session

interface Interpreter {
  fun interpret(fileActions: FileActions, sessionHandler: (Session) -> Unit): List<Session>
}
