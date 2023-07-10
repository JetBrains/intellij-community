// "Implement members" "false"
// ACTION: Extract 'A' from current file
// ACTION: Make internal

interface I {
    fun foo()
}

@Suppress("NOT_A_MULTIPLATFORM_COMPILATION")
expect <caret>class A : I
