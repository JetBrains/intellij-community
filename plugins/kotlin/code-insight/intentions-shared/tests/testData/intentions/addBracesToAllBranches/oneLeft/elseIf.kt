// IS_APPLICABLE: false
fun test(x: Int) {
    if (x == 0) {
        foo()
    } else <caret>if (x == 1) bar() else {
        baz()
    }
}

fun foo() {}
fun bar() {}
fun baz() {}
