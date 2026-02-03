// PROBLEM: none
// WITH_STDLIB
fun foo(p: (String) -> Unit) {
    p("")
}

fun use() {
    foo(
        fun(i<caret>t: String) {
            print("foo")
        },
    )
}