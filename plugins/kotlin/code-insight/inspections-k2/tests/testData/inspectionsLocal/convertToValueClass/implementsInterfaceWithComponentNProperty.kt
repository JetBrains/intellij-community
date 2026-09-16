// PROBLEM: Data class can be converted to value class
// FIX: Convert to value class
// CHOSEN_OPTION: No extra functions
// COMPILER_ARGUMENTS: -XXLanguage:+FullValueClasses

interface I { val component1: String }

data<caret> class User(override val component1: String): I
