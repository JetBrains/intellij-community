// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtObjectDeclaration
// OPTIONS: usages

interface Foo {
    companion obj<caret>ect : () -> Unit by Impl
}

object Impl : () -> Unit {
    override fun invoke() {}
}

fun test() {
    Foo()
}

// IGNORE_K2_LOG
