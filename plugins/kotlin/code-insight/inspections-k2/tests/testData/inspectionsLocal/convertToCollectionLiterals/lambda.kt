// COMPILER_ARGUMENTS: -Xcollection-literals
// PROBLEM: none

fun main() {
    val x: MutableList<Int?> = [1, null]
    x.mapNotNullTo(hashSetOf()) { it }.ifEmpty { empty<caret>Set() }}
}