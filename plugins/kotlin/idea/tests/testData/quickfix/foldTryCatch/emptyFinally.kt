// "Lift assignment out of 'try' expression" "true"
// WITH_STDLIB

fun foo() {
    val x: Int
    try {
        x = 1
    } catch (e: Exception) {
        <caret>x = 2
    } finally {}
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.LiftAssignmentOutOfTryFix