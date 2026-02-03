// FIX: "Replace with '?: error(...)'"
// WITH_STDLIB

fun foo(p: Array<String?>) {
    val v = p[0]
    <caret>assert(v != null, { "Should be not null" })
}