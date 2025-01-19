// PROBLEM: none
// WITH_STDLIB
class UsedInPropertyAnnotated(@JvmField <caret>val x: Int) {
    val y = x
}