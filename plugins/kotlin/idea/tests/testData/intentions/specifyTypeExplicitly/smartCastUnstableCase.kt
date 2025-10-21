var o: Any = ""
fun foo() {
    if (o !is String) return
    val s<caret> = o
}
// AFTER-WARNING: Variable 's' is never used