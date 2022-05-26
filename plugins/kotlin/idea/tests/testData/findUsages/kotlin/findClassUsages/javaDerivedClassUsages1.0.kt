// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtClass
// OPTIONS: derivedClasses
interface X {

}

open class <caret>A: X {

}

open class C : Y {

}

open class Z : A() {

}

// FIR_COMPARISON