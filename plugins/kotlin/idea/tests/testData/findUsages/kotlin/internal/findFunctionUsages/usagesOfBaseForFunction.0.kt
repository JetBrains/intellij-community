// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: usages, skipImports
// PSI_ELEMENT_AS_TITLE: "fun foo(): Unit"

interface A {
    @Suppress("WRONG_MODIFIER_CONTAINING_DECLARATION")
    internal fun foo()
}

class B : A {
    override fun foo() {} // Find usages gives no results
}

fun main(a: A) {
    a.<caret>foo()
}

// for KT-3769 Find usages gives no result for overrides

