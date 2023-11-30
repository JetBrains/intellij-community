// FIR_COMPARISON
// FIR_IDENTICAL
interface A {
    fun xxx(p1: Int, p2: Int)
}

interface B {
    fun xxx(singleParameter: Int) {}
}

abstract class C : A, B {
    fun test() {
        xx<caret>
    }
}

// WITH_ORDER
// EXIST: { itemText: "xxx", tailText: "(singleParameter: Int)" }
// EXIST: { itemText: "xxx", tailText: "(p1: Int, p2: Int)" }
// NOTHING_ELSE
