// IS_APPLICABLE: false
fun test(x: Int) {
    if (x == 0) foo() else if (x == 1) bar() else {
        baz()
    }<caret>
}

fun foo() {}
fun bar() {}
fun baz() {}
