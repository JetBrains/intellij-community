// WITH_STDLIB
fun foo() {}
fun bar() {}

fun test(s: String?, b: Boolean) {
    s?.also {
        if (b) {
            foo()
            return
        } <caret>else {
            bar()
            return
        }
    }
}
