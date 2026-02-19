// AFTER-WARNING: Parameter 'l' is never used
// TODO: seems a bug
// AFTER-WARNING: The corresponding parameter in the supertype 'I' is named 'a'. This may cause problems when calling this function with named arguments.
// AFTER-WARNING: The corresponding parameter in the supertype 'I' is named 'a1'. This may cause problems when calling this function with named arguments.
// AFTER-WARNING: The corresponding parameter in the supertype 'I' is named 'a2'. This may cause problems when calling this function with named arguments.

fun interface I {
    fun action(a: String, a1: Int, a2: Long)
}

fun foo(l: Long) {}

fun test() {
    <caret>I { _, _, a -> foo(a) }
}