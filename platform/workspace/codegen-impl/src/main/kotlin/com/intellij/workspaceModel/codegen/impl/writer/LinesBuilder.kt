package com.intellij.workspaceModel.codegen.impl.writer

class LinesBuilder(val result: StringBuilder) {
  var first = true

  fun line(str: String = "") {
    lineNoNl(str)
    result.append('\n')
  }

  fun lineNoNl(str: String) {
    if (first) {
      first = false
    }

    result.append(str)
  }

  fun <T> list(c: Collection<T>, f: T.() -> String = { "$this" }) {
    c.forEach {
      line(it.f())
    }
  }

  inline fun section(head: String, s:  LinesBuilder.() -> Unit) {
    lineNoNl(head)
    val sub = LinesBuilder(result)
    sub.result.append("{\n")
    sub.s()
    line("}")
  }

  fun sectionNoBrackets(head: String, s: LinesBuilder.() -> Unit) {
    lineNoNl(head)
    val sub = LinesBuilder(result)
    sub.result.append("\n")
    sub.s()
  }
}

inline fun lines(lines: LinesBuilder.() -> Unit): String {
  val result = StringBuilder()
  LinesBuilder(result).lines()
  return result.toString()
}