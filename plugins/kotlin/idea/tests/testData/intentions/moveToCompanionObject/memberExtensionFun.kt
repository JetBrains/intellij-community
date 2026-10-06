// K2_AFTER_ERROR: ARGUMENT_TYPE_MISMATCH
class Sample {
    val v = 1
    fun Int.cbFu<caret>nL() = v + this
}

fun Sample.use() {
    // Incorrect argument substitution after move, issue: KTIJ-39338
    5.cbFunL()
}
