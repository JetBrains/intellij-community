// PROBLEM: none
// COMPILER_ARGUMENTS: -XXLanguage:+FullValueClasses
// K2_ERROR: INCOMPATIBLE_MODIFIERS
// K2_ERROR: INCOMPATIBLE_MODIFIERS

class Outer {
    inner <caret>data class User(val firstName: String, val lastName: String)
}
