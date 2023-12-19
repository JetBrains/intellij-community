// "Remove parameter 'x'" "false"
// ACTION: Add 'block =' to argument
// ACTION: Convert parameter to receiver
// ACTION: Enable 'Types' inlay hints
// ACTION: Enable a trailing comma by default in the formatter
// ACTION: Put parameters on separate lines
// ACTION: Rename to _
// ACTION: Specify return type explicitly

fun foo(block: (String, Int) -> Unit) {
    block("", 1)
}

fun bar() {
    foo(fun(x<caret>: String, y: Int) = Unit)
}
