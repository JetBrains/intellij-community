// "Delete expression" "true"
fun foo(a: String) {
    if (<caret>1 !is Int) {
        bar()
    }
}

fun bar() {}