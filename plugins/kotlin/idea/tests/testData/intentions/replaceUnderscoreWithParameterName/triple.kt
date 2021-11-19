// WITH_STDLIB
// AFTER-WARNING: Variable 'third' is never used

fun foo(t: Triple<String, Int, Boolean>) {
    val (_, _, <caret>_) = t
}