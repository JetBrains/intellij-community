actual interface BaseMethodOption {
    actual fun firstFun()
}

class BaseMethodOptionImplJs : BaseMethodOption {
    override fun firstFun() {}
}

fun testBaseMethodOptionJs(b: BaseMethodOption, impl: BaseMethodOptionImplJs) {
    b.firstFun()
    impl.firstFun()
}