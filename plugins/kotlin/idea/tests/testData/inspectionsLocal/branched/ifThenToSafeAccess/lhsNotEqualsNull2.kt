// HIGHLIGHT: WARNING
// FIX: Replace 'if' expression with safe access expression
fun maybeFoo(): String? {
    return "foo"
}

fun test(): String? {
    val foo = maybeFoo()
    <caret>if (foo != null)
        foo.length
    else
        null
    return foo
}
