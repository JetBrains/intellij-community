// "Add 'fun' modifier to 'I'" "true"
interface I {
    fun f()
}

fun foo(i: I, j: Int) {}

fun test() {
    val x = foo(<caret>I {}, 2)
}
