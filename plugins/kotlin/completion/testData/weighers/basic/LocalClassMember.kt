// FIR_COMPARISON
// FIR_IDENTICAL

fun test() {
    class SomeClass(
        val age: Int
    ) {
        val other: String = ""
    }
    val someClass = SomeClass(5)
    val a = someClass.<caret>
}

// ORDER: age
// ORDER: other
