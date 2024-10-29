// FIR_IDENTICAL
interface T {
    val a1: Byte
    val a2: Short
    val a3: Int
    val a4: Long
    val a5: Float
    val a6: Double
    val a7: Char
    val a8: Boolean
}

class C : T {<caret>
}

// MEMBER: "a1: Byte"
// MEMBER: "a2: Short"
// MEMBER: "a3: Int"
// MEMBER: "a4: Long"
// MEMBER: "a5: Float"
// MEMBER: "a6: Double"
// MEMBER: "a7: Char"
// MEMBER: "a8: Boolean"