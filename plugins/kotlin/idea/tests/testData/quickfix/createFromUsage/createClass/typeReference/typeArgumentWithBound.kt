// "Create class 'Foo'" "true"
// K2 TODO: improve generated class type arguments when "expected type" is fixed
// K2_AFTER_ERROR: Type argument is not within its bounds: must be subtype of 'I'.
interface I

fun <T : I> foo() {}

fun x() {
    foo<<caret>Foo>()
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createClass.CreateClassFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinClassAction