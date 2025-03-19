// HIGHLIGHT: WARNING
// FIX: Replace 'if' expression with safe access expression
fun maybeFoo(): String? {
    return "foo"
}

fun <T> doSomething(a: T) {}

fun main(args: Array<String>) {
    val foo = maybeFoo()
    doSomething(foo)
    i<caret>f (foo != null) {
        foo.length
    }
    else {
        null
    }
}
