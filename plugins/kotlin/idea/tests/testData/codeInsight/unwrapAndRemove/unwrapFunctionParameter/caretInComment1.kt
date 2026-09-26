// OPTION: 0
fun test() {
    val i = 1
    foo(/* <caret> */ 1, 2)
}

fun foo(i: Int, i2: Int) {}
