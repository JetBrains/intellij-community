// PROBLEM: Data class can be converted to value class
// FIX: Convert to value class
// CHOSEN_OPTION: Generate 'componentN' function
// COMPILER_ARGUMENTS: -XXLanguage:+FullValueClasses

interface I { operator fun component1(): String = "Hello" }

data<caret> class User(val firstName: String, val lastName: String, private val secret: String): I