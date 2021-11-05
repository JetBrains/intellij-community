// IS_APPLICABLE: false
fun main() {
    foo(<caret>b = 1, a = 3)
}

fun foo(c: Int = 2, a: Int, b: Int) {
}