// COMPILER_ARGUMENTS: -Xcollection-literals
// PROBLEM: none
fun test(x: Set<Int>) { }

fun test(x: List<Int>) { }


fun main() {
    test(setOf<caret>(1, 2, 3))
}