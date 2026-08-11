// "Make 'A' 'abstract'" "false"
// ERROR: Class 'A' is not abstract and does not implement abstract member public abstract fun foo(): String defined in I
// ERROR: Primary constructor is required for value class
// ACTION: Create test
// ACTION: Extract 'A' from current file
// ACTION: Implement members
// ACTION: Rename file to A.kt
// K2_AFTER_ERROR: ABSENCE_OF_PRIMARY_CONSTRUCTOR_FOR_VALUE_CLASS
// K2_AFTER_ERROR: ABSTRACT_MEMBER_NOT_IMPLEMENTED
// K2_ERROR: ABSENCE_OF_PRIMARY_CONSTRUCTOR_FOR_VALUE_CLASS
// K2_ERROR: ABSTRACT_MEMBER_NOT_IMPLEMENTED
interface I {
    fun foo(): String
}

inline class A<caret> : I
