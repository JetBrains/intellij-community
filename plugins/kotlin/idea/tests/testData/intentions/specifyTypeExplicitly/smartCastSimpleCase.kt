fun foo(o: Any) {
    if (o !is String) return
    val s<caret> = o
}
// AFTER-WARNING: Variable 's' is never used
// IGNORE_K1