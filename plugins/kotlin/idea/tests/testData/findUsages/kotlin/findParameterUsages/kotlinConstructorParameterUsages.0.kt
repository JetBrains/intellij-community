// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtParameter
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "foo: T"
open class A<T>(<caret>foo: T) {
    init {
        println(foo)
    }

    val t: T = foo

    fun usage(): A<String> {
        return A(foo = ":)")
    }
}

fun usage() {
    A(foo = ":)")
}

