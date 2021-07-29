interface T : A

fun foo() {
    open class X : A

    fun bar() {
        class Y : X()

        class Z : T
    }
}
