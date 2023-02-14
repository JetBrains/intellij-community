// OPTION: 0
fun test() {
    val i = 1
    val test = Test()
    test.te<caret>st.qux(i)
}

class Test {
    val test: Test
        get() = Test()
    fun qux(i: Int) = 1
}
