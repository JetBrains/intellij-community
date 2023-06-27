// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtParameter
// OPTIONS: usages, overridingMethods
// PSI_ELEMENT_AS_TITLE: "t: T"

open class MyClass {

    open fun <T> foo(<caret>t: T): T {
        println(t)
        return t
    }

}

class F : MyClass() {
    override fun <T> foo(t: T): T {
        val t2: T = t
        return t2
    }

    fun <T> foo(t: T, t2: T) {}
}