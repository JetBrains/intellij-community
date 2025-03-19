// MODE: return
// IGNORE_K2
// TODO: KTIJ-16537 and KT-73473
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