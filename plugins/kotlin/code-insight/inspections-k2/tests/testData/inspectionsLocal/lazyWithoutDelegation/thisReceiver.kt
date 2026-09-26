class A {
    private val p =<caret> lazy { "hello" }

    fun test() {
        println(this.p.value)
    }
}
