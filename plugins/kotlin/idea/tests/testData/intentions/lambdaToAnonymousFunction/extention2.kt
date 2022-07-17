// AFTER-WARNING: Parameter 'args' is never used
// AFTER-WARNING: Parameter 'f' is never used
class Foo
fun baz(f: Foo.(i: Int, j: Int) -> Int) {}

fun main(args: Array<String>) {
    baz { i, <caret>j -> i + j }
}