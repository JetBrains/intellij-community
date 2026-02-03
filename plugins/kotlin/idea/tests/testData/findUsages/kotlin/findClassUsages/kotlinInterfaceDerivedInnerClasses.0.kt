// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtClass
// OPTIONS: derivedClasses
// PSI_ELEMENT_AS_TITLE: "interface X"
interface <caret>X {

}

open class A : X {

}

interface Y : X {

}

