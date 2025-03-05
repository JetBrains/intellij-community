// K2_ERROR: Unsupported [dynamic type].
// K2_AFTER_ERROR: Unsupported [dynamic type].
// ERROR: Unsupported [Dynamic types are not supported in this context]
// AFTER_ERROR: Unsupported [Dynamic types are not supported in this context]

fun foo() {
    fun <T> bar(c: () -> dynamic, f: () -> T): Unit {}
    bar({
            val a = 1
            Unit
        }) {
        val a = 1
        Unit<caret>
    }
}