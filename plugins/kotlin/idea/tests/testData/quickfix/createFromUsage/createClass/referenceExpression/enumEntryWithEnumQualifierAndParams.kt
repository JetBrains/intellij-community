// "Create enum constant 'A'" "true"
// ERROR: No value passed for parameter 'n'
package p

fun foo() = X.<caret>A

enum class X(n: Int) {

}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createClass.CreateClassFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinClassAction