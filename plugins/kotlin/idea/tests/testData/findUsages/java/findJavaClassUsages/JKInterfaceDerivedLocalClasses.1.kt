interface T : A

fun foo() {
    open class X : A

    fun bar() {
        open class Y : X()

        class Z : T
    }
}
