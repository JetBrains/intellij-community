fun foo1() {}
fun foo2() {}

fun test(b: Boolean) {
    if (b) foo1() <caret>else
        /* aaa */ foo2() // bbb
}
