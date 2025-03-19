// "Create class 'Foo'" "true"
// ERROR: No value passed for parameter 'n'
// K2_AFTER_ERROR: No value passed for parameter 'n'.

open class A(n: Int)

fun test(): A = <caret>Foo(2, "2")
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createClass.CreateClassFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinClassAction