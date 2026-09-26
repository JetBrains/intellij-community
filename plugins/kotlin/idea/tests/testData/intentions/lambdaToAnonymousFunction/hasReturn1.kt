// AFTER-WARNING: Parameter 'f' is never used
fun foo(f: (Int) -> String) {}

fun test() {
    foo {<caret> return@foo "$it" }
}