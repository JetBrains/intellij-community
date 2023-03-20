// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtParameter
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "foo: String"
class Test(@JvmField val <caret>foo: String) {
}
