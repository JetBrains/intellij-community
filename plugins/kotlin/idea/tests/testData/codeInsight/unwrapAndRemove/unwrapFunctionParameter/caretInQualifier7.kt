// OPTION: 0
fun test() {
    val test = Test()
    test<caret>.test.test.qux { val i = 1 }
}

class Test {
    val test: Test
        get() = Test()
    fun qux(body: () -> Unit) = 1
}