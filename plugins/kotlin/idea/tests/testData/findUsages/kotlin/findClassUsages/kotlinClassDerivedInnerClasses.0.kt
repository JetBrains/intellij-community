
// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtClass
// OPTIONS: derivedClasses
// PSI_ELEMENT_AS_TITLE: "class A : X"
interface X {

}

open class <caret>A: X {

}

interface Y : X {

}
