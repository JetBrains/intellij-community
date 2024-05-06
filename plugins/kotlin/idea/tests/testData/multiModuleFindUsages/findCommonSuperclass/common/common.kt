// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtClass
// OPTIONS: usages, constructorUsages, expected

expect open class O<caret>Class(i: Int)
expect class Inheritor : OClass