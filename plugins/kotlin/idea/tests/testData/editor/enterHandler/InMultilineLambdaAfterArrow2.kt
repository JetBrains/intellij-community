fun foo(a: String, b: String, l: (String) -> Unit) {}

fun testIndent() {
  foo(a = "a",
      b = "b") { aaa -><caret>
    println()
  }
}

// SET_INT: INDENT_SIZE=2