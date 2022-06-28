// "Implement members" "false"
// ACTION: Do not show return expression hints
// ACTION: Extract 'A' from current file
// ACTION: Make internal

interface I {
    fun foo()
}

@Suppress("UNSUPPORTED_FEATURE")
expect <caret>class A : I
