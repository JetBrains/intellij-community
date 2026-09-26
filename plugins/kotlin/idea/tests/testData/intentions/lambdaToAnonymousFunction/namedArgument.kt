// AFTER-WARNING: Parameter 'f' is never used
// AFTER-WARNING: Parameter 'name' is never used
fun baz(name: String, f: (Int) -> String) {}

fun test() {
    baz(name = "", f = <caret>{ "$it" })
}