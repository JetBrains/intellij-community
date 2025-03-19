// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtFunction
// OPTIONS: usages, overridingMethods
actual interface BaseMethodOption {
    actual fun fir<caret>stFun()
}

class BaseMethodOptionImplJs : BaseMethodOption {
    override fun firstFun() {}
}

fun testBaseMethodOptionJs(b: BaseMethodOption, impl: BaseMethodOptionImplJs) {
    b.firstFun()
    impl.firstFun()
}