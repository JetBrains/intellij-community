// FIR_IDENTICAL
interface I {
    @Deprecated
    fun foo()
}

class C : I {
    <caret>
}

// MEMBER: "~foo(): Unit~"