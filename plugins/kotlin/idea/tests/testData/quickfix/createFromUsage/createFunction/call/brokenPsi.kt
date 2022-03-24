// "Create extension function 'X.Companion.callSomethingNew'" "true"

class X {
    fun callee() {
        X.<caret>callSomethingNew(123)
    }
    fun test(x:Int): Unit {