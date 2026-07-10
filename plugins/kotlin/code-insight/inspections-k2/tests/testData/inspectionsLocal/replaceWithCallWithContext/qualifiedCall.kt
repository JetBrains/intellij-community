// PROBLEM: none
// WITH_RUNTIME
// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.4

object Foo {
    fun with(x: String, block: String.() -> Unit) = x.block()
}

fun main() {
    Foo.<caret>with("hi") { println(this) }
}