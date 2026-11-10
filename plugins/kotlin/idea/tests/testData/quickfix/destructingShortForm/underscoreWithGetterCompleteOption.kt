// "Convert to a full name-based destructuring form" "true"
// COMPILER_ARGUMENTS: -Xname-based-destructuring=complete

class User {
    val name: String = "Alice"
    var getterCalls: Int = 0
    val sideEffect: Int get() = ++getterCalls
}

fun test(user: User): String {
    val (<caret>_ = sideEffect, name) = user
    return name
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeInsight.inspections.ConvertNameBasedDestructuringShortFormToFullFix
