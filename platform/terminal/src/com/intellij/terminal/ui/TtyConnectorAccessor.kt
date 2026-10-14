// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.ui

import com.jediterm.terminal.TtyConnector
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer

class TtyConnectorAccessor {
  private val ttyConnectorFuture: CompletableFuture<TtyConnector> = CompletableFuture()
  var ttyConnector: TtyConnector?
    get() = ttyConnectorFuture.getNow(null)
    set(value) {
      ttyConnectorFuture.complete(value)
    }

  fun executeWithTtyConnector(consumer: Consumer<TtyConnector>) {
    ttyConnectorFuture.thenAccept(consumer)
  }
}