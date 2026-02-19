// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtTypeParameter
// OPTIONS: usages
open class A<<caret>T>(foo: T, list: List<T>) {
    init {
        fun T.bar() {}

        foo.bar()
    }

    val t: T = foo
    val T.d: Boolean get() = true
    fun bar(t: T): T = t

    fun <R: T> ff() {}
}

