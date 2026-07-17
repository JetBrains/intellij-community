// PROBLEM: none
// ERROR: Companion object of enum class 'C' is uninitialized here
// K2_ERROR: UNINITIALIZED_ENUM_COMPANION
enum class C(val i: Int) {
    ONE(<caret>C.K)
    ;

    companion object {
        const val K = 1
    }
}