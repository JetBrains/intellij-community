// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtParameter
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "p: String"
open class A(open var p: String)

class B(override var p<caret>: String) : A("dummy")

fun foo(a: A, b: B) {
    val c = a.p
    val d = b.p
    a.p = ""
    b.p = ""
}