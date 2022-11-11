// FIR_IDENTICAL
// FIR_COMPARISON
class Some() {
    fun methodName() {
        this.<caret>
    }
}

fun Some.first() {
}

// EXIST: first