class Outer {
    open class X : A()

    @Suppress("LOCAL_INTERFACE_NOT_ALLOWED", "INTERFACE_WITH_SUPERCLASS")
    interface T : A

    class Inner {
        open class Y : X()

        class Z : Y(), T
    }
}
