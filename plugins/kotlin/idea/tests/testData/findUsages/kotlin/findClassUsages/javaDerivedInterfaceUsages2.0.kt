
// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtClass
// OPTIONS: derivedInterfaces
// PSI_ELEMENT_AS_TITLE: "interface X"
interface <caret>X {

}

open class A : X {

}

open class C : Y {

}

open class Z : A() {

}
