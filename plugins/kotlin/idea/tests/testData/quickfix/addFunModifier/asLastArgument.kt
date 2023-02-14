// "Add 'fun' modifier to 'I'" "true"
interface I {
    fun f()
}

fun foo(i: I) {}

fun test() {
    val x = foo(<caret>I {})
}
