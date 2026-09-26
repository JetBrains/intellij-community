// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: overloadUsages
// PSI_ELEMENT_AS_TITLE: "fun topLevelFun(): Unit"

class A

fun topLevelFun(s: String) {}
fun A.topLevelFun() {}

class C {
    fun topLev<caret>elFun() {}

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
