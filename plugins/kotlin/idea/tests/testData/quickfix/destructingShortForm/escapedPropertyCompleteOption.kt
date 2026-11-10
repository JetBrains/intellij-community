// "Convert to a full name-based destructuring form" "true"
// COMPILER_ARGUMENTS: -Xname-based-destructuring=complete

class User(val `when`: Int)

fun test(user: User): Int {
    val (<caret>value = `when`) = user
    return value
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeInsight.inspections.ConvertNameBasedDestructuringShortFormToFullFix
