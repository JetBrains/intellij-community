// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtProperty
// OPTIONS: usages
// OPTIONS: skipRead
// PSI_ELEMENT_AS_TITLE: "var v"


fun foo() {
    var <caret>v = 1
    (v) = 2
    print(v)
    ++v
    v--
    print(-v)
    v += 1
    (v) -= 1
}
