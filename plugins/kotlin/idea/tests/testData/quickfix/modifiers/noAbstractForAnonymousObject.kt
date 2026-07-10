// "Make 'object : T {}' abstract" "false"
// ACTION: Implement members
// ACTION: Split property declaration
// ACTION: Convert object literal to class
// ERROR: Object is not abstract and does not implement abstract member public abstract fun foo(): Unit defined in T
// K2_AFTER_ERROR: ABSTRACT_MEMBER_NOT_IMPLEMENTED
// K2_ERROR: ABSTRACT_MEMBER_NOT_IMPLEMENTED
interface T {
    fun foo()
}

fun test() {
    val o = <caret>object : T {}
}
