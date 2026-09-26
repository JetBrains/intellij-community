// COMPILER_ARGUMENTS: -Xcollection-literals
fun test(x: Set<Int>) {}
fun test(x: String) {}

fun main() {
    test(setOf<caret>(1, 2, 3))
}
