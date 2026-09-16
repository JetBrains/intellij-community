// PROBLEM: Data class can be converted to value class
// FIX: Convert to value class
// CHOSEN_OPTION: Generate 'copy' function
// COMPILER_ARGUMENTS: -XXLanguage:+FullValueClasses

interface Named {
    val firstName: String
}

<caret>data class User(override val firstName: String, val lastName: String) : Named
