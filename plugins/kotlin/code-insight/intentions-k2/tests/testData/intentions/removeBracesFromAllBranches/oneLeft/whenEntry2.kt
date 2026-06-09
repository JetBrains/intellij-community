// IS_APPLICABLE: false
fun test(x: Int) {
    when (x) {
        0 -> {
            foo()
        }<caret>

        else -> bar()
    }
}

fun foo() {}
fun bar() {}
fun baz() {}
