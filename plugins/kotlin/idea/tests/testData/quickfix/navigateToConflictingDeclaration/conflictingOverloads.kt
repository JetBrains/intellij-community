// "Show conflicting 'myFunction' declarations" "true"
// SHOULD_BE_AVAILABLE_AFTER_EXECUTION
// K2_AFTER_ERROR: Conflicting overloads:<br>fun myFunction(): Unit
// K2_AFTER_ERROR: Conflicting overloads:<br>fun myFunction(): Unit
// IGNORE_K1
package test

fun myFunction<caret>() {}

fun myFunction() {}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ShowConflictingDeclarationsAction
