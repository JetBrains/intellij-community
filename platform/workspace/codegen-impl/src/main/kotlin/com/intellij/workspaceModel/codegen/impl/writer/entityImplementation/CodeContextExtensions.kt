package com.intellij.workspaceModel.codegen.impl.writer.entityImplementation

import com.intellij.workspaceModel.codegen.impl.dsl.CodeContext

internal fun CodeContext.`if`(condition: String, block: CodeContext.() -> Unit) {
  section("if ($condition)", block)
}

internal fun CodeContext.ifElse(condition: String, ifBlock: CodeContext.() -> Unit, elseBlock: (CodeContext.() -> Unit)) {
  section("if ($condition)", ifBlock)
  section("else", elseBlock)
}


internal fun CodeContext.lineComment(text: String) {
  line("// $text")
}
