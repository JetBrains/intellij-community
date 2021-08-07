// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtClass
// OPTIONS: derivedClasses
open class B : A() {

}

open class C : Y {

}

open class Z : A() {

}

class U : Z() {

}
