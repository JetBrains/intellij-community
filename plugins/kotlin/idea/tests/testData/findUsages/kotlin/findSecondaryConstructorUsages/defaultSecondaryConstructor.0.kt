// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtSecondaryConstructor
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "constructor B()"
open class B {
    <caret>constructor() {

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

// IGNORE_PLATFORM_JS: KTIJ-29704
// IGNORE_PLATFORM_NATIVE: KTIJ-29704

