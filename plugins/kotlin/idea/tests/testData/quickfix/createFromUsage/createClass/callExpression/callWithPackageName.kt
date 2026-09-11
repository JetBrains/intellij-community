// "Create class 'Foo'" "true"
// K2_ERROR: UNRESOLVED_REFERENCE

package Foo

fun test() = <caret>Foo(2, "2")
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinClassAction