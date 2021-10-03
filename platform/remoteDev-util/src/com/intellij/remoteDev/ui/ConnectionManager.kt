package com.intellij.remoteDev.ui

import org.jetbrains.annotations.Nls
import java.net.URI

interface ConnectionManager {

  @Nls
  fun getProductName() : String

  fun connect(userName: String, uri: URI, onDone: () -> Unit)

  val canClose: Boolean
  fun close()
}