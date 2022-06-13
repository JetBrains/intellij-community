package com.intellij.workspaceModel.codegen.deft.model

class Src(val name: String, val contents: () -> CharSequence) {
  fun pos(pos: Int) = SrcPos(this, pos)
  fun range(range: IntRange) = SrcRange(this, range)
}

data class SrcPos(val src: Src, val pos: Int) : Comparable<SrcPos> {
  val lineCol by lazy {
    var i = 0
    var line = 1
    var lineStart = 0
    val text = src.contents()
    while (i < pos) {
      val c = text[i]
      if (c == '\n') {
        line++
        lineStart = i + 1
      }
      i++
    }

    while (i < text.length && text[i] != '\n') i++
    val lineEnd = i - 1

    SrcLineCol(this, line, lineStart..lineEnd)
  }

  override fun compareTo(other: SrcPos): Int {
    return if (src == other.src) pos.compareTo(other.pos)
    else src.name.compareTo(other.src.name)
  }

  override fun toString(): String = lineCol.toString()
}

class SrcLineCol(val srcPos: SrcPos, val lineNum: Int, val lineRange: IntRange) {
  val col: Int get() = srcPos.pos - lineRange.first
  val line: SrcRange get() = srcPos.src.range(lineRange)
  override fun toString(): String = "${srcPos.src.name}:$lineNum:$col"
}

class SrcRange(val src: Src, val range: IntRange) {
  val text: String by lazy {
    src.contents().subSequence(range).toString()
  }
  val first: SrcPos get() = src.pos(range.first)
  val last: SrcPos get() = src.pos(range.last)
  val length: Int get() = range.last - range.first + 1

  fun show(message: String? = null) = buildString {
    val lineCol = first.lineCol
    val line = lineCol.line
    append(lineCol.toString()).append("\n")
    if (last <= line.last) {
      append(line.text).append("\n")
      append(" ".repeat(lineCol.col)).append("^".repeat(this@SrcRange.length))
      if (message != null) append(" $message")
      append("\n")
    }
    else {
      if (message != null) append(message).append("\n")
      append(" ".repeat(lineCol.col)).append("v".repeat(line.range.last - range.first)).append("\n")
      append(line.text).append("\n")
      append("TODO: print rest lines")
      append("TODO: print ^ pointer for last line")
    }
  }

  override fun toString(): String = "`$text` at $first"
}