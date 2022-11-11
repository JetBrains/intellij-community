val s = ""
val i = 123

fun f(expr1: Any, expr2: String, expr3: Int) {
    foo(<caret>123) // this call is resolved to foo(Int) but types from all signatures should participate
}

fun foo(p1: String, p2: Any) {
}

fun foo(p1: Int) {
}

// ABSENT: expr1
// EXIST: expr2
// EXIST: expr3
// EXIST: s
// EXIST: i
