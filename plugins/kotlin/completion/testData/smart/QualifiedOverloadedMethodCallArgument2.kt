val s = ""
val i = 123

fun f(c: C?, expr1: Any, expr2: String, expr3: Int, expr4: Char) {
    c?.foo(<caret>abc)
}

class C {
    fun foo(p1: String, p2: Any) {
    }

    fun foo(p1: Int) {
    }

    fun foo(p1: Char) {
    }
}

// ABSENT: expr1
// EXIST: expr2
// EXIST: expr3
// EXIST: expr4
// EXIST: s
// EXIST: i
