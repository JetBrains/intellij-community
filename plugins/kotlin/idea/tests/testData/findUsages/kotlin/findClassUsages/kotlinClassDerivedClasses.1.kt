// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtClass
// OPTIONS: derivedClasses
// PSI_ELEMENT_AS_TITLE: ""
open class B : A() {

}

open class C : Y {

}

open class Z : A() {

}

class U : Z() {

}

class InheritTypeAlias : TA()
