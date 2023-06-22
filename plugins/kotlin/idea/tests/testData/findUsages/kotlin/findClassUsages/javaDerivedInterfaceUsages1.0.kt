
// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtClass
// OPTIONS: derivedInterfaces
// PSI_ELEMENT_AS_TITLE: "class A : X"
interface X {

}

open class <caret>A: X {

}

open class C : Y {

}

open class Z : A() {

}
