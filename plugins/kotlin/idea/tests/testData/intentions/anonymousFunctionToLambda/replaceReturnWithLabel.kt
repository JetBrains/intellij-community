// AFTER-WARNING: Parameter 'args' is never used
fun foo(f: () -> Int) {
    f()
}

fun main(args: String) {
    foo(<caret>fun(): Int {
        val a = 1
        if (a > 1) {
            return 1
        }
        return 2
    })
}