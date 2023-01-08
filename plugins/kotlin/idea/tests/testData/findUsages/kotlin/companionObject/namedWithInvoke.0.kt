// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtObjectDeclaration
// OPTIONS: usages

interface Foos {
    companion object <caret>Na {
        operator fun invoke() = 1
    }
}
