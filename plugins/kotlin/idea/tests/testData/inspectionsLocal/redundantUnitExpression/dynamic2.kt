// PROBLEM: none
// ERROR: Unsupported [Dynamic types are not supported in this context]
// K2_ERROR: UNSUPPORTED

fun foo() {
    fun bar(f: () -> dynamic): Unit {}
    bar({
            val a = 1
            Unit<caret>
        })
}