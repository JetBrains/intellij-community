// "Convert to a full name-based destructuring form" "true"
// COMPILER_ARGUMENTS: -Xname-based-destructuring=name-mismatch

data class User(val name: String, val age: Int)

fun test(user: User) {
    user.let { (name, other<caret>Name) -> }
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeInsight.inspections.ConvertNameBasedDestructuringShortFormToFullFix
