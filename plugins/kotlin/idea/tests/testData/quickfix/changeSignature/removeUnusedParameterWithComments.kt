// "Remove parameter 'bar'" "true"
fun foo(foo: String, <caret>bar: String) {
    println(foo)
}

fun test() {
    foo(
        "foo", // foo comment
        "bar"
    )
}

fun println(s: String) {}