// "Make '<init>' public" "false"
// "Make '<init>' internal" "false"
// ACTION: Introduce import alias
// ERROR: This type is sealed, so it can be inherited by only its own nested classes or objects
// K2_AFTER_ERROR: Local class cannot extend a sealed class.

sealed class SealedClass

fun test() {
    class Test : <caret>SealedClass()
}
