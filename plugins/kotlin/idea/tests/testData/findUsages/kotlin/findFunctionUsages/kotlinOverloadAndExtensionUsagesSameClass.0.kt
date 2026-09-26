// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: overloadUsages
// PSI_ELEMENT_AS_TITLE: "fun topLevelFun(): Unit"

class A

class C {
    fun topLev<caret>elFun() {}
    fun topLevelFun(s: String) {}
    fun A.topLevelFun() {}

    fun m(a: A, b: B) {
        topLevelFun()
        a.topLevelFun()
        topLevelFun("")
        b.topLevelFun()
    }
}

class B {
    fun topLevelFun() {
    }
}
