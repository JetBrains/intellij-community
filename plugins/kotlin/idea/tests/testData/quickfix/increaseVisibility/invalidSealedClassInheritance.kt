// "Make '<init>' public" "false"
// "Make '<init>' internal" "false"
// ACTION: Do not show return expression hints
// ACTION: Introduce import alias
// ERROR: This type is sealed, so it can be inherited by only its own nested classes or objects

sealed class SealedClass

fun test() {
    class Test : <caret>SealedClass()
}
