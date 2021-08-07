// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtProperty
// OPTIONS: usages
// FIR_IGNORE

package server

open class A<T> {
    internal open var <caret>foo: T = TODO()
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
