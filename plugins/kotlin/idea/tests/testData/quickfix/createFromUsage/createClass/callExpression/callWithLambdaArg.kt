// "Create class 'Foo'" "true"
// K2_ERROR: Unresolved reference 'Foo'.

fun test() {
    val a = <caret>Foo(2, "2") { p: Int -> p + 1 }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createClass.CreateClassFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinClassAction