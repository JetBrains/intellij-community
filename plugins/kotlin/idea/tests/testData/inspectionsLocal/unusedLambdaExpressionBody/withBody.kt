// "Remove '=' token from function declaration" "true"

fun foo() {
    <caret>bar()
}

fun f() {}

fun bar() = { i: Int ->
    f()
}