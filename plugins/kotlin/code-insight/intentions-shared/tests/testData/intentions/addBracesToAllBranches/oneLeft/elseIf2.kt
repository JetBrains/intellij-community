// IS_APPLICABLE: false
fun test(x: Int) {
    if (x == 0) {
        foo()
    } else if (x == 1) bar()<caret> else {
        baz()
    }
}

fun foo() {}
fun bar() {}
fun baz() {}
