// "Simplify expression" "true"
fun foo(a: String) {
    if (<caret>1 is Int) {
        bar(1)
    } else {
        bar(2)
    }
}

fun bar(i: Int) {}