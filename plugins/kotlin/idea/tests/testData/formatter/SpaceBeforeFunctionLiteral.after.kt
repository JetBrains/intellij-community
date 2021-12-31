fun test(some: (Int) -> Int) {
}

fun String.invoke(action: (String) -> Unit) {
    action(this)
}

fun foo() {
    test() { it }
    "foo" { println(it) }
    "bar" { println(it) }
}