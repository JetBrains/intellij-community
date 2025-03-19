// PROBLEM: none
open class Base(protected open <caret>val x: Int)

class UsedOverridden(override val x: Int) : Base(x) {
    val y = x
}