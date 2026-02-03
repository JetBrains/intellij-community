
fun <R> String.fold(initial: R, operation: (acc: R, Char) -> R): R = TODO()

fun foo(p: Int) {
    "abc".fold(1) { <caret> }
}

// IGNORE_K2
// ORDER: "acc, c ->"
// ORDER: "acc: Int, c: Char ->"
