// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtTypeAlias
// OPTIONS: usages
object OOO {
    operator fun invoke(i: Int) {}
}

typealias <caret>Alias = OOO

fun f2() {
    Alias
    val a: Alias
    Alias(10)
}
