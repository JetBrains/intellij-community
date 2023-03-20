fun test() {
    call(*arrayOf(1, 2, "foo"))
}

fun call(vararg values: Any) {}