// ERROR: Unsupported [Dynamic types are not supported in this context]
// AFTER_ERROR: Unsupported [Dynamic types are not supported in this context]
// K2_AFTER_ERROR: UNSUPPORTED
// K2_ERROR: UNSUPPORTED

fun foo() {
    fun <T> bar(c: () -> T, f: () -> dynamic): Unit {}
    bar({
            val a = 1
            Unit<caret>
        }) {
        val a = 1
        Unit
    }
}