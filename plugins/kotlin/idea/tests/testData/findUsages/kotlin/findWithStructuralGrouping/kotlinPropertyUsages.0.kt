// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtProperty
// GROUPING_RULES: org.jetbrains.kotlin.idea.base.searching.usages.KotlinDeclarationGroupingRule
// OPTIONS: usages

package server

open class A<T> {
    open var <caret>foo: T = TODO()
}

open class B : A<String>() {
    override var foo: String
        get() {
            println("get")
            return super<A>.foo
        }
        set(value: String) {
            println("set:" + value)
            super<A>.foo = value
        }
}

// FIR_COMPARISON