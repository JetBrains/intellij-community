fun f(expr1: Any, expr2: String, expr3: Int) {
    C().foo(<caret>)
}

class C {
    public fun foo(p1: String, p2: Any) {
    }

    private fun foo(p1: Int) {
    }
}

// ABSENT: expr1
// EXIST: expr2
// ABSENT: expr3
