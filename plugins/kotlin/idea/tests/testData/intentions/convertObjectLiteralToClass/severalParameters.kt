// AFTER-WARNING: Variable 'x' is never used
fun testMe() {
    val s1: String? = ""
    val s2: String? = ""

    val x = <caret>object {
        fun f(): Int {
            return (s1?.length ?: 0) + (s2?.length ?: 0)
        }
    }
}