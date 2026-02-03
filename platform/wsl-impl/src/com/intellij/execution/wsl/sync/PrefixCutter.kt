// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl.sync

import com.intellij.openapi.diagnostic.thisLogger

/**
 * Random [token] should be inserted into string and then string could be processed with [getAfterToken]
 * Usage: add some junk to string, then [token] then useful data. Call [getAfterToken] on string and get useful data only
 */
internal class PrefixCutter {
  val token: String = System.currentTimeMillis().toString()
  fun getAfterToken(text: String): String {
    val indexOf = text.indexOf(token)
    if (indexOf == -1) {
      throw Exception("No $token found in $text")
    }
    if (indexOf != 0) {
      thisLogger().info("Cutting line: ${text.substring(0, indexOf)}")
    }
    return text.substring(indexOf + token.length)
  }
}