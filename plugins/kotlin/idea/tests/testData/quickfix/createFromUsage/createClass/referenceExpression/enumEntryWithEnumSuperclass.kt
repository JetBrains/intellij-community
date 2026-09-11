// "Create enum constant 'A'" "true"
// K2_ERROR: UNRESOLVED_REFERENCE
package p

fun foo(): E = E.<caret>A

enum class E {

}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinClassAction