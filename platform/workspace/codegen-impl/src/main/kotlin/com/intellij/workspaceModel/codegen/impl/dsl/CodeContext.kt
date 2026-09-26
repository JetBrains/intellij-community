package com.intellij.workspaceModel.codegen.impl.dsl

interface CodeContext : GeneratorContext {
  val parentContext: GeneratorContext
  
  val result: String

  fun line(string: String)

  operator fun String.unaryPlus() {
    line(this)
  }

  fun lineNoNl(string: String)

  fun section(head: String, body: CodeContext.() -> Unit)

  fun sectionNoBrackets(head: String, block: CodeContext.() -> Unit)
}
