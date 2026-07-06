// "Show conflicting 'myProperty' declarations" "true"
// SHOULD_BE_AVAILABLE_AFTER_EXECUTION
// K2_AFTER_ERROR: REDECLARATION
// K2_AFTER_ERROR: REDECLARATION
// K2_ERROR: REDECLARATION
// K2_ERROR: REDECLARATION

package test

val myProperty<caret>: Int = 42

val myProperty: String = "hello"
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ShowConflictingDeclarationsAction
