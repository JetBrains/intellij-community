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