// PROBLEM: none
class UsedInSetter(<caret>val x: Int) {
    var y: Int = 0
        get() = field
        set(arg) { field = x + arg }
}