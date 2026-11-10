// "Convert to a full name-based destructuring form" "true"
// COMPILER_ARGUMENTS: -Xname-based-destructuring=only-syntax

data class User(val name: String, val age: Int)

fun test(user: User): Int {
    val (<caret>age, name) = user
    return name + age.length
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeInsight.inspections.ConvertNameBasedDestructuringShortFormToFullFix
