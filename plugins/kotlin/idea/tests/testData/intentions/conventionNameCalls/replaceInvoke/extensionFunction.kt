fun test() {
    class Test()

    operator fun Test.invoke(): Unit = Unit

    val test = Test()
    test.i<caret>nvoke()
}
