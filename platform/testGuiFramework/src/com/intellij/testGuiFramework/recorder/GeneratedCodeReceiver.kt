// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.recorder

import com.intellij.openapi.extensions.ExtensionPointName


interface GeneratedCodeReceiver {

  /**
   * Script generator passes generated code to this method.
   *
   * @param code received generated code
   * @param indentation number of indentation whitespaces of [code]
   */
  fun receiveCode(code: String, indentation: Int)

  companion object {
    val EP_NAME = ExtensionPointName.create<GeneratedCodeReceiver>("com.intellij.generatedCodeReceiver")

    internal fun sendCode(code: String) {
      for (receiver in EP_NAME.extensionList) {
        receiver.receiveCode(code, Writer.indent * ContextChecker.getContextDepth())
      }
    }
  }
}