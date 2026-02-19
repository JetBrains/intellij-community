fun foo(f: (Int, <caret>Boolean) -> String) {
    f(1, false)
}

fun baz(f: (Int, Boolean) -> String) {
    foo(f = f)
}