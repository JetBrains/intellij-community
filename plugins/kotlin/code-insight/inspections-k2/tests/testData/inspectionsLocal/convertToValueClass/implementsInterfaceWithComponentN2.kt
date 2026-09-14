// PROBLEM: Data class can be converted to value class
// FIX: Convert to value class
// CHOSEN_OPTION: No extra functions
// COMPILER_ARGUMENTS: -XXLanguage:+FullValueClasses

interface I { operator fun component2(): String = "Hello" }

data<caret> class User(val firstName: String, val lastName: String, private val secret: String): I