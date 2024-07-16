// PROBLEM: none
open class UsedInOverride(open <caret>val x: Int)

class UserInOverride(override val x: Int) : UsedInOverride(x)