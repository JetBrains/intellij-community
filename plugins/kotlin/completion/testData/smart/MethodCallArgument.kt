val s = ""

fun f(expr1: Any, expr2: String) {
    foo(<caret>)
}

fun foo(p1: String, p2: Any) : String{
}

// ABSENT: expr1
// EXIST: expr2
// EXIST: s
// EXIST: foo
