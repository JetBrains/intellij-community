// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtConstructor
// OPTIONS: usages
annotation class Anno<caret>(val foo: String)

@Anno(foo = "bar")
class Bar