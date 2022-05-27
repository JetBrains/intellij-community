package org.jetbrains.deft.codegen.model

import com.intellij.openapi.diagnostic.logger

private val LOG = logger<Diagnostics>()
class Diagnostics {
  fun add(range: SrcRange, message: String) {
    LOG.info(range.show(message))
  }
}