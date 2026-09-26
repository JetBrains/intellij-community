// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: usages
// CHECK_SUPER_METHODS_YES_NO_DIALOG: no
// PSI_ELEMENT_AS_TITLE: "fun setProp(Int): Unit"
// DISABLE_ERRORS

interface F {
    fun set<caret>Prop(p: Int)
}

abstract class A0 {
    abstract val prop: Int
}
class A (override var prop: Int): A0(), F {
    override fun setProp(p: Int) {
        prop = p
    }

    fun m() {
        prop = 1
    }
}