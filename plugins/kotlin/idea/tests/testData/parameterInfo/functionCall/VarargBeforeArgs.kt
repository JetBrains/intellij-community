fun foo(vararg p0: String, p1: Int, p2: Int) {}

fun test() {
    foo("", p1 = 1, <caret>)
}

