// "Make 'object : T {}' abstract" "false"
// ACTION: Implement members
// ACTION: Split property declaration
// ACTION: Convert object literal to class
// ERROR: Object is not abstract and does not implement abstract member public abstract fun foo(): Unit defined in T
// K2_AFTER_ERROR: Class '<anonymous>' is not abstract and does not implement abstract member:<br>fun foo(): Unit
interface T {
    fun foo()
}

fun test() {
    val o = <caret>object : T {}
}
