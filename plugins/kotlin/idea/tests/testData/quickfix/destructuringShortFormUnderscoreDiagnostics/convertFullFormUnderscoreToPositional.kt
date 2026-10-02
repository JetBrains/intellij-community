// "Convert to positional destructuring syntax with square brackets" "true"
// COMPILER_ARGUMENTS: -Xname-based-destructuring=name-mismatch
// WITH_STDLIB
// K2_ERROR: NAME_BASED_DESTRUCTURING_UNDERSCORE_WITHOUT_RENAMING

data class User(val name: String, val age: Int)

fun test(user: User) {
    (val <caret>_, val age) = user
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.ConvertToPositionalDestructuringFix
