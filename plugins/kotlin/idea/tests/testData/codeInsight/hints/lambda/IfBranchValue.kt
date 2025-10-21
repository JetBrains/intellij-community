// MODE: return
// TODO: KTIJ-16537
fun block(fn: () -> String): String = fn()

fun inAndEx(string: String?): String {
    return block {
        val x = if (string == null) {
            string + "x"
        } else {
            string
        }
        x/*<# ^|[IfBranchValue.kt:162]block #>*/
    }
}