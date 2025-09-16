// AFTER-WARNING: Parameter 'b' is never used
fun main() {
    foo(<caret>(1
            < 2))
}

fun foo(b: Boolean) {}