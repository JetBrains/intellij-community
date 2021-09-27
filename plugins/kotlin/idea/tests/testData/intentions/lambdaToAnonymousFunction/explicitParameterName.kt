// AFTER-WARNING: Parameter 'f' is never used
fun bar(f: (Int, Int) -> String) {}

fun test() {
    bar <caret>{ i, j -> "$i$j" }
}