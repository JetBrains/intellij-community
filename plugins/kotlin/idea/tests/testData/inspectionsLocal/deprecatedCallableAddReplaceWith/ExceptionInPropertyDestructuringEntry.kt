// PROBLEM: none
// K2_ERROR: NON_LOCAL_DESTRUCTURING_DECLARATION

// See KT-7929, EA-76715
class C {
    val (<caret>x, y) = Pair(1, 2)
}