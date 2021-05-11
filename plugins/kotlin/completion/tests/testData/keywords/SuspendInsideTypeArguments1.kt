// FIR_COMPARISON
class Su() {
    fun x() {
        mutableListOf<Pair<<caret>, String>>()
    }
}


// EXIST: in
// EXIST: out
// EXIST: suspend

// NOTHING_ELSE