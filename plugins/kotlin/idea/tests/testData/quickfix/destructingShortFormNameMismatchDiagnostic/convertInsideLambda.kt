// "Convert to a full name-based destructuring form" "true"
// COMPILER_ARGUMENTS: -Xname-based-destructuring=name-mismatch
// WITH_STDLIB

data class User(val name: String, val age: Int)

fun test(user: User) {
    user.let { (<caret>foo, age) -> }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.DestructuringToFullFormFactory$ConvertNameBasedDestructuringToFullFormFix
