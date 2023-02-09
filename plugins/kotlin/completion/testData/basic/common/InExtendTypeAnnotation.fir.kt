// FIR_COMPARISON

class Test : <caret> {
    fun test() {
    }
}

// EXIST: Any, Nothing, Unit, Int, Number, Array
// ABSENT: Thread
// ^ we should not complete declarations from indexes on empty prefix for the performance reasons