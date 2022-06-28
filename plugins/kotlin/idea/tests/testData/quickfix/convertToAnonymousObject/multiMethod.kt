// "Convert to anonymous object" "false"
// ACTION: Do not show return expression hints
// ACTION: Introduce import alias
// ERROR: Interface I does not have constructors
interface I {
    fun foo(): String
    fun bar(): Unit
}

fun test() {
    <caret>I {
    }
}