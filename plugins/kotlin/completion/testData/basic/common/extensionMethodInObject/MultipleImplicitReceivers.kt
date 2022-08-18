class T

object O {
    fun A.fooForA() {}
    fun A.B.fooForB() {}
    fun A.B.C.fooForC() {}
    fun T.fooForT() {}
}

class A {
    inner class B {
        fun T.usage() {
            foo<caret>
        }

        inner class C {}
    }
}

// EXIST: { lookupString: "fooForA", itemText: "fooForA", icon: "nodes/function.svg"}
// EXIST: { lookupString: "fooForB", itemText: "fooForB", icon: "nodes/function.svg"}
// EXIST: { lookupString: "fooForT", itemText: "fooForT", icon: "nodes/function.svg"}
// ABSENT: fooForC