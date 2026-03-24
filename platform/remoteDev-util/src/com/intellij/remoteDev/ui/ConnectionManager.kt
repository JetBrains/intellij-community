// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteDev.ui

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.net.URI

@ApiStatus.Experimental
interface ConnectionManager {

  @Nls
  fun getProductName() : String

  fun connect(userName: String, uri: URI, onDone: () -> Unit)

  val canClose: Boolean
  fun close()
}