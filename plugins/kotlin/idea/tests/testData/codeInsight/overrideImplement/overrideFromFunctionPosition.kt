// FIR_IDENTICAL
interface Some {
    fun foo()
}

class Other {
    fun test() {
        val a = 1<caret>
    }
    fun otherTest() {

    }
}

// MEMBER: "equals(other: Any?): Boolean"
// MEMBER: "hashCode(): Int"
// MEMBER: "toString(): String"