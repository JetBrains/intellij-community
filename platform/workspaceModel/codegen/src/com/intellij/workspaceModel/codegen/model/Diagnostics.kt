package org.jetbrains.deft.codegen.model

class Diagnostics(val appendable: Appendable? = System.out) {
  fun add(range: SrcRange, message: String) {
    appendable?.appendLine(range.show(message))
  }
}