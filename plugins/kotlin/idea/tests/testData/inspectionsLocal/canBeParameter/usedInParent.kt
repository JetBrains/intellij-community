// PROBLEM: none
class UsedInParent(<caret>val x: UsedInParent?) {
    val y = x?.x
}