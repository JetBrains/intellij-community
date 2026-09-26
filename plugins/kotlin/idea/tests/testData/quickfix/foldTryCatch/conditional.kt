// "class org.jetbrains.kotlin.idea.quickfix.LiftAssignmentOutOfTryFix" "false"
// K2_ACTION: "class org.jetbrains.kotlin.idea.k2.codeinsight.fixes.LiftAssignmentOutOfTryFixFactory$LiftAssignmentOutOfTryFix" "false"
// ACTION: Change to 'var'
// DISABLE_ERRORS
// WITH_STDLIB

fun foo(arg: Boolean) {
    val x: Int
    try {
        if (arg) {
            x = 1
        }
    } catch (e: Exception) {
        <caret>x = 2
    }
}