// "Rename variable to 'name'" "false"
// COMPILER_ARGUMENTS: -Xname-based-destructuring=complete
// WITH_STDLIB
// K2_ERROR: UNRESOLVED_REFERENCE
// K2_AFTER_ERROR: UNRESOLVED_REFERENCE

data class User(val name: String, val age: Int)

fun test(user: User) {
    val (<caret>n, age) = user
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.DestructuringRenameFactory$RenameDestructuringEntriesToMatchPropertiesFix