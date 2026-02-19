// OPTION: 0
fun test() {
    val i = 1
    val test = Test()
    te<caret>st.qux(i)
}

class Test {
    fun qux(i: Int) = 1
}
