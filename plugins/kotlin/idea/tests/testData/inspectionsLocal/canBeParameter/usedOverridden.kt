// PROBLEM: none
open class Base(protected open val x: Int)

class UsedOverridden(override <caret>val x: Int) : Base(x) {
    val y = x
}