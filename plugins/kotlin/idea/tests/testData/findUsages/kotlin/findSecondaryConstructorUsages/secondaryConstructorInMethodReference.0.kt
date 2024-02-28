// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtSecondaryConstructor
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "constructor B(String)"
class B(val primary: Int) {
    const<caret>ructor(secondary: String) : this(secondary.length)
}