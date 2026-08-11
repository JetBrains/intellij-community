// "Make test suspend" "true"
// K2_ERROR: ILLEGAL_SUSPEND_FUNCTION_CALL
suspend fun foo() {}

inline fun bar(f: () -> Unit) {
}

fun test() {
    bar {
        <caret>foo()
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddSuspendModifierFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddSuspendModifierFixFactory$AddSuspendModifierFix