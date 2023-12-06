package com.intellij.workspaceModel.codegen.impl.writer

class LinesBuilder(
  val result: StringBuilder,
  val indentLevel: Int,
  val indentSize: Int = 4
) {
  var first = true

  fun line(str: String = "") {
    lineNoNl(str)
    result.append('\n')
  }

  fun lineNoNl(str: String) {
    if (first) {
      first = false
    }

    result.append(" ".repeat(indentLevel * indentSize))
    result.append(str)
  }

  fun <T> list(c: Collection<T>, f: T.() -> String = { "$this" }) {
    c.forEach {
      line(it.f())
    }
  }

  fun section(s: LinesBuilder.() -> Unit) {
    LinesBuilder(result, indentLevel + 1, indentSize).s()
  }

  fun section(head: String, s: LinesBuilder.() -> Unit) {
    lineNoNl(head)
    val sub = LinesBuilder(result, indentLevel+1, indentSize)
    sub.result.append(" {\n")
    sub.s()
    line("}")
  }

  fun sectionNoBrackets(head: String, s: LinesBuilder.() -> Unit) {
    lineNoNl(head)
    val sub = LinesBuilder(result, indentLevel+1, indentSize)
    sub.result.append("\n")
    sub.s()
  }
}

inline fun lines(level: Int = 0, lines: LinesBuilder.() -> Unit): String {
  val result = StringBuilder()
  LinesBuilder(result, level).lines()
  return result.toString()
}