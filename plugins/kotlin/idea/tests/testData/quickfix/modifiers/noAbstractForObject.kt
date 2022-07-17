// "Make 'Some' abstract" "false"
// ACTION: Create test
// ACTION: Extract 'Some' from current file
// ACTION: Implement members
// ACTION: Rename file to Some.kt
// ERROR: Object 'Some' is not abstract and does not implement abstract member public abstract fun foo(): Unit defined in T
interface T {
    fun foo()
}

object <caret>Some : T
