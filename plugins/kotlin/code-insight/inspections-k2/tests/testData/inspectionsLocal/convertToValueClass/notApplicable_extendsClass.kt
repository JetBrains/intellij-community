// PROBLEM: none
// COMPILER_ARGUMENTS: -XXLanguage:+FullValueClasses

abstract class Base

<caret>data class User(val firstName: String, val lastName: String) : Base()
