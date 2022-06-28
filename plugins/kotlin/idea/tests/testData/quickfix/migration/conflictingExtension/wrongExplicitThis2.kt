// "Delete redundant extension property" "false"
// ACTION: Do not show return expression hints
// ACTION: Move to companion object
// ACTION: Remove explicit type specification

class C : Thread() {
    var Thread.<caret>priority: Int
        get() = getPriority()
        set(value) {
            this@C.setPriority(value)
        }
}
