// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtParameter
// OPTIONS: usages, overridingMethods
// PSI_ELEMENT_AS_TITLE: "t2: T"

open class MyClass {
    open fun <T> foo(t: T, <caret>t2: T): T {
        println(t)
        return t2
    }
}

class F : MyClass() {
    override fun <T> foo(t: T, t2: T): T {
        val t3: T = t2
        return t
    }
}