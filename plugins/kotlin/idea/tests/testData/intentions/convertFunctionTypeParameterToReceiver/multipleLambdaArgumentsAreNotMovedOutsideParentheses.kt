// AFTER-WARNING: Parameter 'i' is never used

fun foo(i: () -> Unit, f: (Int, <caret>Boolean) -> String) {
    f(1, false)
}

fun baz(f: (Int, Boolean) -> String) {
    foo({}, f)
}