// "Convert to 'isArrayOf' call" "true"
// WITH_STDLIB
fun test(a: Array<*>) {
    if (a !is <caret>Array<String>) {
    }
}