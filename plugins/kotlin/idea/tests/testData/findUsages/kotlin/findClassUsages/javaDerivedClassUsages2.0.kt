// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtClass
// OPTIONS: derivedClasses
interface <caret>X {

}

open class A : X {

}

open class C : Y {

}

open class Z : A() {

}

// FIR_COMPARISON