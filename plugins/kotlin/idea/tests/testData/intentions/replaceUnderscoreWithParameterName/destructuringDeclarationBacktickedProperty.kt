// AFTER-WARNING: Variable 'when' is never used
data class Data(val `when`: Int)

fun test(data: Data) {
    val (<caret>_) = data
}
