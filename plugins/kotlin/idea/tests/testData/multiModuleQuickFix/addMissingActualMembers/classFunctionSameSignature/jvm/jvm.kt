// "Add missing actual members" "true"
// DISABLE_ERRORS
// IGNORE_K2

actual object <caret>O {
    fun <T> hello(): MutableMap<String, T> {
        TODO("not implemented")
    }
}