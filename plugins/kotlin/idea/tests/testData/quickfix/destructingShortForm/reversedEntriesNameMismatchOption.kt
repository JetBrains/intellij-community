// "Convert to a full name-based destructuring form" "true"
// COMPILER_ARGUMENTS: -Xname-based-destructuring=name-mismatch

data class User(val name: String, val age: Int)

fun test(user: User): Int {
    <caret>val (age, name) = user
    return name + age.length
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeInsight.inspections.ConvertNameBasedDestructuringShortFormToFullFix
