fun foo(p1: Int, p2: Int, p3: Int) {
    val v = <caret>"a" + p1 + '\n' + p2 + '\r' + p3 + '\t'
}
