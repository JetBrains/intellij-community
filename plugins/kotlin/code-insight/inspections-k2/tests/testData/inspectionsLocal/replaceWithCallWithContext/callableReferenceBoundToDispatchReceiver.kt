// PROBLEM: none
// WITH_RUNTIME
// COMPILER_ARGUMENTS: -Xcontext-parameters -Xcallable-references-to-contextual
// LANGUAGE_VERSION: 2.5

class C {
    fun f() {}
}

fun main() {
    <caret>with(C()) {
        print(::f)
    }
}