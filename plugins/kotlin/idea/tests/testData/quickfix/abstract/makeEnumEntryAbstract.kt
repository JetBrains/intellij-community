// "Make 'A' 'abstract'" "false"
// ACTION: Implement members
// ERROR: Class 'A' is not abstract and does not implement abstract member public abstract fun foo(): Unit defined in E
// K2_AFTER_ERROR: ABSTRACT_MEMBER_NOT_IMPLEMENTED_BY_ENUM_ENTRY
// K2_ERROR: ABSTRACT_MEMBER_NOT_IMPLEMENTED_BY_ENUM_ENTRY

enum class E {
    <caret>A;

    abstract fun foo()
}
