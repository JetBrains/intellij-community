// AFTER-WARNING: Parameter 'i' is never used
// AFTER-WARNING: Variable 'a' is never used
fun foo() {
    val a: (Int) -> Unit = { <caret>a -> bar(a) }
}

fun bar(i: Int) {}