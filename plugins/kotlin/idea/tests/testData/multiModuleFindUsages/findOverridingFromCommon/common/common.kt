// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtFunction
// OPTIONS: usages, overridingMethods

expect interface BaseMethodOption {
    actual fun firstFun<caret>()
}

class BaseMethodOptionImpl : BaseMethodOption {
    override fun firstFun() {}
}

fun testBaseMethodOptionCommon(b: BaseMethodOption, impl: BaseMethodOptionImpl) {
    b.firstFun()
    impl.firstFun()
}