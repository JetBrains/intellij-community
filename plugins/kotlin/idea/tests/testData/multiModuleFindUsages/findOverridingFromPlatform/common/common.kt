expect interface BaseMethodOption {
    actual fun firstFun()
}

class BaseMethodOptionImpl : BaseMethodOption {
    override fun firstFun() {}
}

fun testBaseMethodOptionCommon(b: BaseMethodOption, impl: BaseMethodOptionImpl) {
    b.firstFun()
    impl.firstFun()
}