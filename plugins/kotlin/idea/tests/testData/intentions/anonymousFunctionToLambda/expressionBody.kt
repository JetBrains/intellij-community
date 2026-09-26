// AFTER-WARNING: Parameter 'args' is never used
fun foo3(f: () -> Int) {
    f()
}

fun main(args: String) {
    foo3(<caret>fun () = 1)
}