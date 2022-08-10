class T {
    companion object
}

open class A {
    fun T.Companion.foo() {}
}

open class B: A()
open class C: B()
open class D: C()

object DObject: D()

fun usage() {
    T.<caret>
}

// EXIST: { lookupString: "foo", itemText: "foo", icon: "nodes/function.svg"}
