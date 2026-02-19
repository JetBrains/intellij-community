// FIR_IDENTICAL
interface Some {
    fun foo()
}

class Other <caret>{
    fun test() {
        val a = 1
    }
    fun otherTest() {

    }

    override fun hashCode(): Int {
        return super.hashCode()
    }
}

// MEMBER: "equals(other: Any?): Boolean"
// MEMBER: "toString(): String"