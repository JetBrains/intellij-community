// PROBLEM: none
// COMPILER_ARGUMENTS: -Xcontext-parameters -Xcallable-references-to-contextual
// LANGUAGE_VERSION: 2.5
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

context(<caret>s: String)
fun usesChar() {
    foo2 { println(a) }
    foo2(A<String>::usesString)
}