package com.intellij.workspaceModel.codegen.impl.writer.classes

import com.intellij.workspaceModel.codegen.impl.writer.LinesBuilder

internal fun LinesBuilder.lineWrapped(str: String) {
  line()
  line(str)
  line()
}

internal fun LinesBuilder.sectionNl(head: String, s: LinesBuilder.() -> Unit) {
  section(head, s)
  result.append("\n")
}

internal fun LinesBuilder.conditionalLine(predicate: () -> Boolean, head: String, s: LinesBuilder.() -> Unit) {
  if (predicate.invoke()) {
    section(head, s)
    result.append("\n")
  }
}

internal fun <T> LinesBuilder.listNl(c: Collection<T>, f: T.() -> String = { "$this" }) {
  list(c, f)
  result.append("\n")
}

internal fun <T> LinesBuilder.list(c: Collection<T>, func: (LinesBuilder, T) -> Unit) {
  c.forEach {
    func(this, it)
  }
}

internal fun LinesBuilder.`if`(condition: String, s: LinesBuilder.() -> Unit) {
  section("if ($condition)", s)
}

internal fun LinesBuilder.lineComment(text: String) {
  line("// $text")
}

internal fun LinesBuilder.`for`(condition: String, s: LinesBuilder.() -> Unit) {
  section("for ($condition)", s)
}

internal fun LinesBuilder.`else`(s: LinesBuilder.() -> Unit) {
  section("else", s)
}

internal fun LinesBuilder.ifElse(condition: String, s: LinesBuilder.() -> Unit, `else`: (LinesBuilder.() -> Unit)) {
  section("if ($condition)", s)
  section("else", `else`)
}

internal fun <T> LinesBuilder.listBuilder(c: Collection<T>, f: LinesBuilder.(item: T) -> Unit) {
  c.forEach {
    this.f(it)
  }
}

internal fun LinesBuilder.wrapper(): LinesBuilder {
  return LinesBuilder(result, indentLevel+1, indentSize)
}