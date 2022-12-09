// FIR_COMPARISON

class Test {
    fun test() {
        val some : <caret>
    }
}

// EXIST: Any, Nothing, Unit, Int, Number, Array
// ABSENT: Thread
// ^ we should not complete declarations from indexes on empty prefix for the performance reasons