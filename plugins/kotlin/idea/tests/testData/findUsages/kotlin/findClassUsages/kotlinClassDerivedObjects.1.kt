// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtClass
// OPTIONS: derivedClasses
open class Z : A() {

}

object O1 : A()

object O2 : Z()
