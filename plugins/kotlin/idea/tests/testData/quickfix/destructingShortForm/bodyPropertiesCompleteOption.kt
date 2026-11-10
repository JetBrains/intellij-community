// "Convert to a full name-based destructuring form" "true"
// COMPILER_ARGUMENTS: -Xname-based-destructuring=complete

class User {
    val name: String = "Alice"
    val age: Int = 30
}

fun test(user: User): Int {
    val (<caret>age, name) = user
    return age + 1 + name.length
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeInsight.inspections.ConvertNameBasedDestructuringShortFormToFullFix
