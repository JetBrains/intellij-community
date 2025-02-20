// "Create class 'A'" "true"
// K2 TODO: improve generated class type arguments when "expected type" is fixed
// K2_AFTER_ERROR: No type arguments expected for class A : Any.
package p

fun foo(): <caret>A<Int, String> = throw Throwable("")
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createClass.CreateClassFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinClassAction