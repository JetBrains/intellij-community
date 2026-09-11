// "Create object 'A'" "true"
// K2_ERROR: UNRESOLVED_REFERENCE
package p

fun foo() = X.<caret>A

class X {

}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinClassAction