fun foo(f: (Int) -> String) {}

fun test() {
    foo <caret>{
        // comment1
        ""
        // comment2
// AFTER-WARNING: Parameter 'f' is never used
// AFTER-WARNING: Parameter 'it' is never used, could be renamed to _
    }
}