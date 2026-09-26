// AFTER-WARNING: Parameter 'args' is never used
fun foo(f: () -> Int) {
    f()
}

fun main(args: String) {
    foo(<caret>fun(): Int {
        return 1
    })
}