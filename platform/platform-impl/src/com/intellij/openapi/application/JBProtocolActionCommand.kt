// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application

import com.intellij.openapi.diagnostic.logger

class JBProtocolActionCommand : JBProtocolCommand(COMMAND) {
  private companion object {
    val LOG = logger<JBProtocolActionCommand>()
    const val COMMAND = "action"
    const val EXECUTE = "execute"
    const val PROJECT = "project"
    const val ID = "id"
  }

  override fun perform(target: String, parameters: MutableMap<String, String>) = throw NotImplementedError()
}