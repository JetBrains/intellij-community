// FIR_IDENTICAL
// FIR_COMPARISON
class Su() {
    fun x() {
        mutableListOf<Pair<<caret>, String>>()
    }
}


// EXIST: in
// EXIST: out
// EXIST: suspend
// EXIST: context

// NOTHING_ELSE