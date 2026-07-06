// "Specify 'Unit?' return type for enclosing function 'foo'" "true"
// WITH_STDLIB
// K2_ERROR: NULL_FOR_NONNULL_TYPE

fun foo() {
    return n<caret>ull
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeCallableReturnTypeFix$ForEnclosing
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeTypeQuickFixFactories$UpdateTypeQuickFix