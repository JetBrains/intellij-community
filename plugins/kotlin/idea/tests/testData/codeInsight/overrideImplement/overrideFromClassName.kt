// FIR_IDENTICAL
interface Some {
    fun foo()
}

class <caret>Other {
    fun test() {
        val a = 1
    }
    fun otherTest() {

    }
}

// MEMBER: "equals(other: Any?): Boolean"
// MEMBER: "hashCode(): Int"
// MEMBER: "toString(): String"