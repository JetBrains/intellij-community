// IS_APPLICABLE: false
fun test() {
    val i = 1
    <caret>foo(123, 456)
}

fun foo(i: Int) {}

