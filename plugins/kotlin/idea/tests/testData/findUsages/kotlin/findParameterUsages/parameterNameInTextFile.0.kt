// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtParameter
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "param: String"
fun foo(<caret>param: String) {

}

fun bar() {
    foo(param = "")
}

