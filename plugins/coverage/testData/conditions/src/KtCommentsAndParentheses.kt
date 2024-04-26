class KtCommentsAndParentheses {
  fun extraBrackets(a: Boolean): String {
    return if ((a)) "1" else "2"
  }

  fun extraBracketsExpression(a: Boolean, b: Boolean): String {
    return if ((a && (b))) "1" else "2"
  }

  fun extraComment(a: Boolean): String {
    return if (a /* comment */) "1" else "2"
  }

  fun extraCommentInsideExpression(a: Boolean, b: Boolean): String {
    return if (a /* comment */ && b) "1" else "2"
  }

  fun extraBracketsInIf(a: Boolean) {
    if ((((((a)))))) println(1) else println(2)
  }

  fun commentInSwitch(x: Int) {
    when ( /* comment */x) {
      1 -> println(1)
      2 -> println(2)
      else -> println(0)
    }
  }

  fun commentInSwitchExpression(x: Int, y: Int) {
    when (y +  /* comment */x) {
      1 -> println(1)
      2 -> println(2)
      else -> println(0)
    }
  }

  fun extraBracketsInSwitch(x: Int) {
    when ((((((x)))))) {
      1 -> println(1)
      2 -> println(2)
      else -> println(0)
    }
  }
}
