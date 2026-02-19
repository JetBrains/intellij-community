// AFTER-WARNING: Parameter 'test' is never used
class Foo(val a: String)

fun test(test: Fo<caret>o.() -> Unit) {
}

fun box(): String {
    test {
        a == "123"
    }

    return "OK"
}