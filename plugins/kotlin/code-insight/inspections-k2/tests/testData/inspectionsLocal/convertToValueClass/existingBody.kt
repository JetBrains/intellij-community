// PROBLEM: Data class can be converted to value class
// FIX: Convert to value class
// CHOSEN_OPTION: Generate 'componentN' function
// COMPILER_ARGUMENTS: -XXLanguage:+FullValueClasses

<caret>data class User(val firstName: String, val lastName: String) {
    fun fullName(): String = "$firstName $lastName"
}
