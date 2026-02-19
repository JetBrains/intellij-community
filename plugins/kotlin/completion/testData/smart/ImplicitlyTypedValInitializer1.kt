fun foo(p1: String, p2: Int) {
    val v = <caret>"a"
}

// EXIST: p1
// ABSENT: p2
// IGNORE_K2
