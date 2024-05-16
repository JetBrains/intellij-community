// FIX: Replace 'if' expression with safe access expression
// WITH_STDLIB
// HIGHLIGHT: INFORMATION
// IGNORE_K2
fun maybeFoo(): String? {
    return "foo"
}

fun convert(x: String, y: Int) = ""

fun foo(it: Int) {
    val foo = maybeFoo()
    <caret>if (foo == null) else convert(foo, it)
}

