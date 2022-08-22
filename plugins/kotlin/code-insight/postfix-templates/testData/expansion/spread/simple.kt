fun test() {
    call(arrayOf(1, 2, "foo")<caret>)
}

fun call(vararg values: Any) {}