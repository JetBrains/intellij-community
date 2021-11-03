interface T
interface B: T
interface C: T

object A {
    fun Any.fooForAny() {}

    fun T.fooForT() {}
    fun B.fooForB() {}
    fun C.fooForC() {}

    fun <TT> TT.fooForAnyGeneric() {}
    fun <TT: T> TT.fooForTGeneric() {}
    fun <TT: B> TT.fooForBGeneric() {}
    fun <TT: C> TT.fooForCGeneric() {}

    fun fooNoReceiver() {}
}

fun usage(b: B) {
    b.foo<caret>
}

// EXIST: { lookupString: "fooForAny", itemText: "fooForAny", icon: "nodes/function.svg"}

// EXIST: { lookupString: "fooForT", itemText: "fooForT", icon: "nodes/function.svg"}
// EXIST: { lookupString: "fooForB", itemText: "fooForB", icon: "nodes/function.svg"}

// EXIST: { lookupString: "fooForTGeneric", itemText: "fooForTGeneric", icon: "nodes/function.svg"}
// EXIST: { lookupString: "fooForBGeneric", itemText: "fooForBGeneric", icon: "nodes/function.svg"}

// ABSENT: fooForC
// ABSENT: fooForCGeneric
// ABSENT: fooNoReceiver