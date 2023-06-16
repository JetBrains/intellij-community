// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtProperty
// OPTIONS: usages, skipWrite
// PSI_ELEMENT_AS_TITLE: "var foo: T"
package server

open class A<T>(t: T) {
    open var <caret>foo: T = t
}

open class B : A<String>("") {
    override var foo: String
        get() {
            println("get")
            return ""
        }
        set(value: String) {
            println("set:" + value)
        }
}

