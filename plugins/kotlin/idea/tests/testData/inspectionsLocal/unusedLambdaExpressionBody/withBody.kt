// "Remove '=' token from function declaration" "true"
// ERROR: Unresolved reference: println
fun foo() {
    <caret>bar()
}

fun bar() = { i: Int ->
    println()
}