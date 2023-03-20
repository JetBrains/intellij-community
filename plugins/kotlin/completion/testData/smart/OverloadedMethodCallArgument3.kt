fun f(expr1: Any, expr2: String, expr3: Int) {
    foo("abc", <caret>)
}

fun foo(p1: String, p2: String) {
}

fun foo(p1: Int, p2: Int) {
}

// ABSENT: expr1
// EXIST: expr2
// ABSENT: expr3
