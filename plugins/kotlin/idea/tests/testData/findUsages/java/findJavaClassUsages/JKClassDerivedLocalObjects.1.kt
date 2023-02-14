open class T : A()

fun foo() {
    val O1 = object : A() {}

    fun bar() {
        val O2 = object : T() {}
    }
}

