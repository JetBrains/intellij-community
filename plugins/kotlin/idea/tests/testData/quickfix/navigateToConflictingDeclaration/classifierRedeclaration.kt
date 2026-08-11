// "Show conflicting 'MyClass' declarations" "true"
// SHOULD_BE_AVAILABLE_AFTER_EXECUTION
// K2_AFTER_ERROR: CLASSIFIER_REDECLARATION
// K2_AFTER_ERROR: CLASSIFIER_REDECLARATION
// K2_ERROR: CLASSIFIER_REDECLARATION
// K2_ERROR: CLASSIFIER_REDECLARATION

package test

class MyClass<caret> {}

class MyClass {}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ShowConflictingDeclarationsAction
