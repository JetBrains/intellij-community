// "Make '<init>' public" "false"
// "Make '<init>' internal" "false"
// ACTION: Introduce import alias
// ERROR: This type is sealed, so it can be inherited by only its own nested classes or objects
// K2_AFTER_ERROR: SEALED_SUPERTYPE_IN_LOCAL_CLASS
// K2_ERROR: SEALED_SUPERTYPE_IN_LOCAL_CLASS

sealed class SealedClass

fun test() {
    class Test : <caret>SealedClass()
}
