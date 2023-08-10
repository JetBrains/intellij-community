// IS_APPLICABLE: false
fun test(x: Int) {
    if (x == 0) <caret>{
        foo()
    } else if (x == 1) bar() else baz()
}

fun foo() {}
fun bar() {}
fun baz() {}
