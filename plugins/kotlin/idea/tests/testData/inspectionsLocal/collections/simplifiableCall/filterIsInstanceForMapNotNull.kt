
// WITH_STDLIB

fun bla() {
    val xs = listOf(1, "", true)
    xs.<caret>mapNotNull { it as? String }
}
