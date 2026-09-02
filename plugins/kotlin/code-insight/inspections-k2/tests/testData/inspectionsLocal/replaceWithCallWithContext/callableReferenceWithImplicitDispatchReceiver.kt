// "Replace 'with' with 'context'" "true"
// WITH_RUNTIME
// COMPILER_ARGUMENTS: -Xcontext-parameters -Xcallable-references-to-contextual
// LANGUAGE_VERSION: 2.5

class C {
    context(s: String)
    fun f() {
        s.uppercase()
    }
}

context(_: String)
fun C.getF() = ::f

fun main() {
    <caret>with("hi") {
        print(C().getF())
    }
}