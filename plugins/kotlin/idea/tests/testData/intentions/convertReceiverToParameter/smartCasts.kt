// WITH_STDLIB

fun Num<caret>ber.myFunction() {
    if (this is Int) {
        println(this)
    }
}

fun usage(foo: Any) {
    if (foo is Number) {
        foo.myFunction()
    }
}