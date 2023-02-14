// WITH_STDLIB
// AFTER-WARNING: Parameter 'f' is never used
fun foo(f: (Map.Entry<Int, Int>) -> Int) {}

fun bar() {
    foo { <caret>it ->
        it.key + it.value
    }
}