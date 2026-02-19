actual interface BaseMethodOption {
    actual fun firstFun()
}

class BaseMethodOptionImplJvm : BaseMethodOption {
    override fun firstFun() {}
}

fun testBaseMethodOptionJvm(b: BaseMethodOption, impl: BaseMethodOptionImplJvm) {
    b.firstFun()
    impl.firstFun()
}
