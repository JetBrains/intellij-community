// PROBLEM: none
// K2_ERROR: Companion object of enum class 'C' is uninitialized here.
// ERROR: Companion object of enum class 'C' is uninitialized here
enum class C(val i: Int) {
    ONE(<caret>C.K)
    ;

    companion object {
        const val K = 1
    }
}