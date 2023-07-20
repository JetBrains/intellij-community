// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtClass
// OPTIONS: usages, constructorUsages
// PSI_ELEMENT_AS_TITLE: "class KK"
fun test() {
    val kk: KK = <caret>KK()
}

class KK internal constructor()


