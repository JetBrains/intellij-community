// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: overloadUsages
// PSI_ELEMENT_AS_TITLE: "fun A.topLevelFun(): Unit"

class A

fun topLevelFun() {}
fun topLevelFun(s: String) {}
fun A.topLev<caret>elFun() {}

fun m(a: A, b: B) {
    topLevelFun()
    a.topLevelFun()
    topLevelFun("")
    b.topLevelFun()
}

class B {
    fun topLevelFun() {
    }
}
