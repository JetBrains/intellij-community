// IS_APPLICABLE: false
fun test(x: Int) {
    when (x) {
        0 -> {
            foo()
        }
        else -> bar()<caret>
    }
}

fun foo() {}
fun bar() {}
fun baz() {}
