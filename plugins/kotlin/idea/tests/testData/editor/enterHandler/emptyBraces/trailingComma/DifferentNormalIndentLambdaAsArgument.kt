fun main() {
  foo(
    a = {<caret>},
  )
}

fun foo(a: () -> Unit) {

}

// NORMAL_INDENT_SIZE: 2