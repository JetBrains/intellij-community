// FIR_IDENTICAL
// FIR_COMPARISON
fun <T> genericFoo(p: Int){}
fun <T> genericFoo(c: Char){}

fun foo() {
    genericFoo<<caret>
}

// EXIST: String
// EXIST_K2: String
// EXIST: kotlin
// EXIST_K2: kotlin.
// ABSENT: defaultBufferSize
// ABSENT: readLine
