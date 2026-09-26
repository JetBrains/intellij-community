// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: overloadUsages
// PSI_ELEMENT_AS_TITLE: "fun foo(): Unit"
class FooBar {
    fun f<caret>oo() {}
    fun String.foo() {}

    fun testKotlinMemberExtensionFun(s: String) {
        s.foo()
    }
}