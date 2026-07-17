// "Create enum constant 'A'" "true"
// ERROR: No value passed for parameter 'n'
// K2_AFTER_ERROR: NO_VALUE_FOR_PARAMETER
// K2_ERROR: UNRESOLVED_REFERENCE
package p

fun foo() = X.<caret>A

enum class X(n: Int) {

}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createClass.CreateClassFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinClassAction