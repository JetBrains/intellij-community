package com.intellij.workspaceModel.codegen.utils

class LinesBuilder(
  val result: StringBuilder,
  val indent: String,
  val delayed: String? = null
) {
  var first = true

  fun line(str: String = "") {
    lineNoNl(str)
    result.append('\n')
  }

  fun lineNoNl(str: String) {
    if (first) {
      first = false
      if (delayed != null) result.append(delayed)
    }

    result.append(indent)
    result.append(str)
  }

  fun <T> list(c: Collection<T>, f: T.() -> String = { "$this" }) {
    c.forEach {
      line(it.f())
    }
  }

  fun section(s: LinesBuilder.() -> Unit) {
    LinesBuilder(result, "$indent    ").s()
  }

  fun section(head: String, s: LinesBuilder.() -> Unit) {
    lineNoNl(head)
    val sub = LinesBuilder(result, "$indent    ")
    sub.result.append(" {\n")
    sub.s()
    line("}")
  }

  fun sectionNoBrackets(head: String, s: LinesBuilder.() -> Unit) {
    lineNoNl(head)
    val sub = LinesBuilder(result, "$indent    ")
    sub.result.append("\n")
    sub.s()
  }
}

inline fun lines(indent: String = "", lines: LinesBuilder.() -> Unit): String {
  val result = StringBuilder()
  LinesBuilder(result, indent).lines()
  return result.toString()
}