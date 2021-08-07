fun foo() {
    open class T : A()

    val a = object : A() {}

    fun bar() {
        val b = object : T() {}
    }
}
