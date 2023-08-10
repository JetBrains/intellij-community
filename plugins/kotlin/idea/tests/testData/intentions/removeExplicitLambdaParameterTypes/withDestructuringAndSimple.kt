// AFTER-WARNING: Destructured parameter 'x' is never used
// AFTER-WARNING: Destructured parameter 'y' is never used
// AFTER-WARNING: Parameter 'i' is never used, could be renamed to _
data class DataIntString(var i: Int, var s: String)
fun destruct(i: Int, s: String, lambda: (DataIntString, Int) -> Unit) = lambda(DataIntString(i, s), i)
fun useDestruct() {
    destruct(0, "a") { (x, y): DataIntString, i: Int<caret> -> }
}