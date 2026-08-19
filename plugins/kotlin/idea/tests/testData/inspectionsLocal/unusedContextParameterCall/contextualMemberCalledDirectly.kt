// PROBLEM: none
// COMPILER_ARGUMENTS: -Xcontext-parameters
class A<T>(val a: T) {
    context(s: String)
    fun usesString(): T {
        println(s)
        return a
    }
}

fun foo2(block: A<String>.() -> Unit) {
    val a = A("JetBrains")
    a.block()
}

fun usesChar() {
    <caret>context("hi") {
        foo2 { usesString() }
    }
}