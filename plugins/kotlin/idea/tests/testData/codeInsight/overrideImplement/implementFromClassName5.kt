// FIR_IDENTICAL
interface A {
    fun a1()
    fun a2()
    fun a3()
    fun a4()
    fun a5()
    fun a6()
    fun a7()
    fun a8()
}

class <caret>Test : A {
    fun foo() {
    }

    override fun a3() {
    }

    fun bar() {
    }

    override fun a6() {
    }

    fun baz() {
    }
}

// MEMBER: "a1(): Unit"
// MEMBER: "a2(): Unit"
// MEMBER: "a4(): Unit"
// MEMBER: "a5(): Unit"
// MEMBER: "a7(): Unit"
// MEMBER: "a8(): Unit"