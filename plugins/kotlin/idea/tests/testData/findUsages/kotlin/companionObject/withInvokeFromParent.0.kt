// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtObjectDeclaration
// OPTIONS: usages

open class ClassWithInvoke {
    operator fun invoke() = 1
}

class SimpleClass(i: Int) {
    companion obj<caret>ect : ClassWithInvoke()
}
