// PROBLEM: none
class UsedOutside(<caret>val x: Int)

fun use(): Int {
    val used = UsedOutside(30)
    return used.x
}