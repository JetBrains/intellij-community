// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtProperty
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "var foo"
class Foo {
    companion object {
        @JvmStatic
        var <caret>foo = 1
    }
}
