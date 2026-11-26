// "Convert to a full name-based destructuring form" "true"
// COMPILER_ARGUMENTS: -Xname-based-destructuring=only-syntax -XXLanguage:+FullValueClasses

value class UserX(val firstName: String, val lastName: String)

fun test(user: UserX): String {
    val (<caret>lastName, firstName) = user
    return "$lastName $firstName"
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeInsight.inspections.ConvertNameBasedDestructuringShortFormToFullFix
