// FIR_IDENTICAL
interface I {
    fun foo()
}

class C<caret> : I // comment

// MEMBER: "foo(): Unit"