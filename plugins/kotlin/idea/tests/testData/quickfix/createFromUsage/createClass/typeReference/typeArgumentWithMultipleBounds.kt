// "Create class 'Foo'" "true"
// K2 TODO: improve generated class type arguments when "expected type" is fixed
open class A
interface I

fun <T : I> foo() where T : A {}

fun x() {
    foo<<caret>Foo>()
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createClass.CreateClassFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinClassAction