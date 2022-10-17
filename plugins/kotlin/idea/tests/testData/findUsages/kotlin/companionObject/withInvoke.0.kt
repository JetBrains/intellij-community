// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtObjectDeclaration
// OPTIONS: usages
interface Foos {
    companion ob<caret>ject {
        operator fun invoke() = 1
    }
}
