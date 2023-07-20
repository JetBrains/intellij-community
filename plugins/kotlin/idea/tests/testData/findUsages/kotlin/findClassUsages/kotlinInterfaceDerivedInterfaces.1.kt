// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtClass
// OPTIONS: derivedInterfaces
// PSI_ELEMENT_AS_TITLE: ""
open class B : A() {

}

open class C : Y {

}

open class Z : A() {

}

open class U : Z() {

}

interface D : Y {}
