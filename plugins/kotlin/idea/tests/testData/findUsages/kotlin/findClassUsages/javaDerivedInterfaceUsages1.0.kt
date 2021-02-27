// FIR_COMPARISON
// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtClass
// OPTIONS: derivedInterfaces
interface X {

}

open class <caret>A: X {

}

open class C : Y {

}

open class Z : A() {

}
