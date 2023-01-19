// FIR_COMPARISON
// FIR_IDENTICAL
class Foo {
    fun `with space`() = 10
}

fun test(f: Foo) {
    f.<caret>
}

// ELEMENT: "with space"