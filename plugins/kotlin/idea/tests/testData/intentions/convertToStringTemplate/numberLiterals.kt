fun foo(p1: Int, p2: Int, p3: Int) {
    val v = <caret>"a" + 0xAAA + p1 + 123 + p2 + 1.25 + "b"
}
