// WITH_STDLIB
// AFTER-WARNING: Parameter 'i' is never used
// AFTER-WARNING: Parameter 'i' is never used
// AFTER-WARNING: Variable 'x' is never used
fun test() {
    val x = listOf(1, 2, 3, 4, 5).filterNot<caret> { foo(it) && bar(it) }
}

fun foo(i: Int) = true
fun bar(i: Int) = true