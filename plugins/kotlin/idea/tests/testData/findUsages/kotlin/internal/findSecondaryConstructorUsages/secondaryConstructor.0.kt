// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtSecondaryConstructor
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "constructor B(String)"
open class B {
    constructor() : this("") {

    }

    internal <caret>constructor(s: String) {

    }
}

open class A : B {
    constructor(a: Int) : super("") {

    }
}

class C : B("") {

}

fun test() {
    B("")
}


