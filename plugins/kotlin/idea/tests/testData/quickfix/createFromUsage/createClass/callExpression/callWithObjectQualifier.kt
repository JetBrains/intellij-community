// "Create class 'Foo'" "true"
// K2_ERROR: UNRESOLVED_REFERENCE

object A {

}

fun test() {
    val a = A.<caret>Foo(2)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createClass.CreateClassFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinClassAction