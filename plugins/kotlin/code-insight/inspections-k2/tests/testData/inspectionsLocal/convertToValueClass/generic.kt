// PROBLEM: Data class can be converted to value class
// FIX: Convert to value class
// CHOSEN_OPTION: Generate 'copy' and 'componentN' functions
// COMPILER_ARGUMENTS: -XXLanguage:+FullValueClasses

<caret>data class Box<T>(val value: T)
