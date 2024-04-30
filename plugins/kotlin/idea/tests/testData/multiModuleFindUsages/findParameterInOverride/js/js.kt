// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtParameter
// OPTIONS: usages, overridingMethods

open class D {
    open fun m(<caret>a: String) {
        println(a)
    }
}

class E: D() {
    override fun m(a: String) {
        val p = a
        println(p)
    }
}

// IGNORE_K2_LOG