// AFTER-WARNING: Variable 'x' is never used
fun foo(flag: Boolean) {
    val x: Double<caret>
    x = if (flag) 3.14 else 2.71
}