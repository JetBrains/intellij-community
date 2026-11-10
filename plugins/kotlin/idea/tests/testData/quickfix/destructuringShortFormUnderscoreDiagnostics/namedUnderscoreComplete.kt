// "Convert to a full name-based destructuring form" "true"
// COMPILER_ARGUMENTS: -Xname-based-destructuring=complete
// WITH_STDLIB
// K2_ERROR: NAME_BASED_DESTRUCTURING_UNDERSCORE_WITHOUT_RENAMING

data class User(val name: String, val age: Int, val country: String) {
    var getterCalls: Int = 0
    val sideEffect: Int get() = ++getterCalls
}

fun test(user: User): String {
    val (_ = sideEffect, country, _<caret>) = user
    return country
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.DestructuringToFullFormFactory$ConvertNameBasedDestructuringToFullFormFix
