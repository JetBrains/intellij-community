// IS_APPLICABLE: false
fun test(x: Int) {
    when (x) {
        <caret>0 -> {
            foo()
        }

        else -> bar()
    }
}

fun foo() {}
fun bar() {}
fun baz() {}
