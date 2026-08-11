package com.intellij.workspaceModel.codegen.impl.writer.entityImplementation

import com.intellij.workspaceModel.codegen.impl.dsl.CodeContext

internal fun CodeContext.`if`(condition: String, block: CodeContext.() -> Unit) {
  section("if ($condition)", block)
}

internal fun CodeContext.lineComment(text: String) {
  line("// $text")
}

internal fun CodeContext.`for`(condition: String, block: CodeContext.() -> Unit) {
  section("for ($condition)", block)
}

internal fun CodeContext.`else`(block: CodeContext.() -> Unit) {
  section("else", block)
}

internal fun CodeContext.ifElse(condition: String, ifBlock: CodeContext.() -> Unit, elseBlock: (CodeContext.() -> Unit)) {
  section("if ($condition)", ifBlock)
  section("else", elseBlock)
}

internal fun <T> CodeContext.listBuilder(items: Collection<T>, func: CodeContext.(T) -> Unit) {
  for (item in items) {
    func(item)
  }
}
