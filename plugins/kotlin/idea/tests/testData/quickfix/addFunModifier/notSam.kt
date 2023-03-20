// "Add 'fun' modifier to 'I'" "false"
// DISABLE-ERRORS
// ACTION: Introduce import alias
// ACTION: Split property declaration
interface I {
    fun f() {}
}

fun test() {
    val x = <caret>I {}
}
