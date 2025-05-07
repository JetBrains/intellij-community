// "Add missing actual members" "true"
// DISABLE_ERRORS


actual object <caret>O {
    fun <T> hello(): MutableMap<String, T> {
        TODO("not implemented")
    }
}