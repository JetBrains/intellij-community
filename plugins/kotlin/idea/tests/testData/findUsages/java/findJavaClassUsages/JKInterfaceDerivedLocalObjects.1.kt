fun foo() {
    open class T : A

    val O1 = object : A {}

    fun bar() {
        val O2 = object : T() {}
    }
}
