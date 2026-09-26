// "class org.jetbrains.kotlin.idea.quickfix.LiftAssignmentOutOfTryFix" "false"
// K2_ACTION: "class org.jetbrains.kotlin.idea.k2.codeinsight.fixes.LiftAssignmentOutOfTryFixFactory$LiftAssignmentOutOfTryFix" "false"
// ACTION: Change to 'var'
// ERROR: Val cannot be reassigned
// ERROR: Val cannot be reassigned
// WITH_STDLIB
// K2_AFTER_ERROR: VAL_REASSIGNMENT
// K2_AFTER_ERROR: VAL_REASSIGNMENT
// K2_ERROR: VAL_REASSIGNMENT
// K2_ERROR: VAL_REASSIGNMENT

fun foo() {
    val x = 1
    try {
        val x = 2
        x = 3
    } catch(e: Exception) {
        <caret>x = 4
    }
}