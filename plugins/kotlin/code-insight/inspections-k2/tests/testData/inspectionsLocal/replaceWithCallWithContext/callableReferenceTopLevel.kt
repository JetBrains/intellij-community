// "Replace 'with' with 'context'" "true"
// WITH_RUNTIME
// COMPILER_ARGUMENTS: -Xcontext-parameters -Xcallable-references-to-contextual
// LANGUAGE_VERSION: 2.5

context(x: String)
fun shout(): String {
    return x.uppercase() + "!"
}

fun main() {
    <caret>with("hi") {
        println(::shout)
    }
}