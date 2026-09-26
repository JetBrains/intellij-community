// AFTER-WARNING: Variable 'a' is never used
// AFTER-WARNING: Variable 'b' is never used
// AFTER-WARNING: Variable 'c' is never used
data class Foo(val a: String, val b: String, val c: String)

fun bar(f: Foo) {
    val (<caret>) = f
}