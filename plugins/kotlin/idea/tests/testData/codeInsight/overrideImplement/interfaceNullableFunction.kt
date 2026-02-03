// FIR_IDENTICAL
interface Some {
    fun foo(some : Int?) : Int
}

class SomeOther : Some {
    <caret>
}

// MEMBER: "foo(some: Int?): Int"