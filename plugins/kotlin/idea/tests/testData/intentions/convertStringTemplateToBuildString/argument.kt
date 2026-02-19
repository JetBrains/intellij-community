// PRIORITY: LOW
// WITH_STDLIB
// AFTER-WARNING: Parameter 's' is never used

fun test() {
    foo(<caret>"bar")
}

fun foo(s: String) {}