// "Add 'fun' modifier to 'I'" "true"
interface I {
    fun f()
}

fun test() {
    val x = <caret>I {}
}
