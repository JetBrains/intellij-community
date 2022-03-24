// FIR_IDENTICAL
// FIR_COMPARISON
class Su() {
    fun x() {
        mutableListOf<<caret>>()
    }
}


// EXIST: suspend

// NOTHING_ELSE



