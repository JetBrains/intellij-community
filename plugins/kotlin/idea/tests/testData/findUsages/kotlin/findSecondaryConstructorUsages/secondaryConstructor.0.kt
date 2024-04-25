// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtSecondaryConstructor
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "constructor B(String)"
open class B {
    constructor() : this("") {

    }

    <caret>constructor(s: String) {

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

// IGNORE_PLATFORM_JS: KTIJ-29704
// IGNORE_PLATFORM_NATIVE: KTIJ-29704


