// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtParameter
// OPTIONS: overrides
// PSI_ELEMENT_AS_TITLE: "foo: T"
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

    fun baz(a: A<String>) {
        a.foo = ""
        println(a.foo)
    }
}

open class D : A<String>("") {
    override var foo: String = ""
}

open class E<T>(override var foo: T) : A<T>(foo)

// IGNORE_PLATFORM_JS: Java-specific code
// IGNORE_PLATFORM_NATIVE: Java-specific code

