// "Make 'A' 'abstract'" "false"
// ACTION: Implement members
// ERROR: Class 'A' is not abstract and does not implement abstract member public abstract fun foo(): Unit defined in E
// K2_AFTER_ERROR: Enum entry 'E.A' does not implement abstract member:<br>fun foo(): Unit

enum class E {
    <caret>A;

    abstract fun foo()
}
