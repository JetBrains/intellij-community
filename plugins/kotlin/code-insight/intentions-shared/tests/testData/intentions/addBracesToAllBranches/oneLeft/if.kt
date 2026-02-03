// IS_APPLICABLE: false
fun test(x: Int) {
    <caret>if (x == 0) foo() else if (x == 1) {
        bar()
    } else {
        baz()
    }
}

fun foo() {}
fun bar() {}
fun baz() {}
