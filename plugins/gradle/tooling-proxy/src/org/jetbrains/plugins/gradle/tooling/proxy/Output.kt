// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.tooling.proxy

import java.io.Serializable

sealed class Output(val text: String) : org.gradle.launcher.daemon.protocol.Message(), Serializable {
  override fun toString(): String {
    return "${this.javaClass.simpleName}(text='$text')"
  }
}

class StandardError(text: String) : Output(text)
class StandardOutput(text: String) : Output(text)