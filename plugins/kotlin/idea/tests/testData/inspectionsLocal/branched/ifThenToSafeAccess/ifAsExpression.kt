// HIGHLIGHT: WARNING
// FIX: Replace 'if' expression with safe access expression
// IGNORE_K2
fun maybeFoo(): String? {
    return "foo"
}

fun main(args: Array<String>) {
    val foo = maybeFoo()
    val x = <caret>if (foo == null) {
        null
    }
    else {
        foo.length
    }
}
