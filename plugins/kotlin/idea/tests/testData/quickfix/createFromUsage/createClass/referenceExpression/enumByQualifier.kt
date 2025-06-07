// "Create enum 'A'" "true"
// ERROR: Unresolved reference: B
// K2_AFTER_ERROR: Unresolved reference 'B'.
package p

fun foo() = <caret>A.B
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createClass.CreateClassFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinClassAction