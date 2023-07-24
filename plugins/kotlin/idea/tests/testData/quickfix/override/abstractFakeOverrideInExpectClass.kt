// "Implement members" "true"
// ACTION: Extract 'A' from current file
// ACTION: Implement members
// ACTION: Make 'A' 'abstract'
// ACTION: Make internal

interface I {
    fun foo()
}

@Suppress("NOT_A_MULTIPLATFORM_COMPILATION")
expect <caret>class A : I
