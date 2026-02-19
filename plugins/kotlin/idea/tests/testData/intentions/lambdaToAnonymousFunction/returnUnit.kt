// AFTER-WARNING: Parameter 'f' is never used
// AFTER-WARNING: Parameter 'i' is never used
fun unit(f: (Int) -> Unit) {}

fun foo(i: Int) {}

fun test() {
    unit {<caret>
        foo(it)
        foo(it)
    }
}