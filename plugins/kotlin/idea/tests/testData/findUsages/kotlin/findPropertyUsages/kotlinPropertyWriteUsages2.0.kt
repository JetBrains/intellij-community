// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtParameter
// OPTIONS: usages, skipRead
// PSI_ELEMENT_AS_TITLE: "foo: T"

package server

open class A<T>(open var <caret>foo: T)

open class B : A<String>("") {
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

