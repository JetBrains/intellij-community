// HIGHLIGHT: WARNING
// FIX: Replace 'if' expression with safe access expression
fun maybeFoo(): String? {
    return "foo"
}

fun test(): String? {
    val foo = maybeFoo()
    i<caret>f (null == foo)
        null
    else
        foo.length
    return foo
}
