// "Create class 'B'" "true"
// ERROR: Unresolved reference: C
// K2_AFTER_ERROR: Unresolved reference 'C'.
package p

fun foo() = A.<caret>B.C

class A {

}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createClass.CreateClassFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinClassAction