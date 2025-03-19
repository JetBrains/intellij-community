// "Remove parameter 'x'" "false"
// ACTION: Convert to anonymous function
// ACTION: Convert to single-line lambda
// ACTION: Enable a trailing comma by default in the formatter
// ACTION: Move lambda argument into parentheses
// ACTION: Remove explicit lambda parameter types (may break code)
// ACTION: Rename to _

fun foo(block: (String, Int) -> Unit) {
    block("", 1)
}

fun bar() {
    foo { x<caret>: String, y: Int ->
        y.hashCode()
    }
}
