// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtObjectDeclaration
// OPTIONS: usages
interface Foos {
    companion ob<caret>ject {
    }
}
interface Bar {
    operator fun Foos.Companion.invoke() {}
}

