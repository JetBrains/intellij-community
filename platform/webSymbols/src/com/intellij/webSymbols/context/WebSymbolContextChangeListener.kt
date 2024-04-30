// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.context

import com.intellij.util.messages.Topic

fun interface WebSymbolContextChangeListener {

  fun contextMayHaveChanged()

  companion object {
    @JvmStatic
    val TOPIC: Topic<WebSymbolContextChangeListener> = Topic(WebSymbolContextChangeListener::class.java)
  }

}