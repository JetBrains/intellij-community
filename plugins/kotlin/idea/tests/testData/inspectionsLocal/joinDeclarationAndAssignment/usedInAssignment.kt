// PROBLEM: none
fun foo(o: Any) {
}

fun bar() {
    <caret>lateinit var lambda: () -> Unit
    lambda = {
        foo(lambda)
    }
}