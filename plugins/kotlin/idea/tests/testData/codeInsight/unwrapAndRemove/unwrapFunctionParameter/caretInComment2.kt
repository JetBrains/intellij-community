// OPTION: 0
fun test() {
    val i = 1
    foo(1 /* <caret> */, 2)
}

fun foo(i: Int, i2: Int) {}
