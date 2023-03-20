// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtSecondaryConstructor
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "constructor B()"
open class B {
    internal <caret>constructor() {

    }

    constructor(a: Int) : this() {

    }
}

class A : B {
    constructor(a: Int) : super() {

    }

    constructor() {

    }
}

class C : B() {

}

fun test() {
    B()
}


