class A {
    private fun <T> receiver(block: () -> T): T = block()

    operator fun <T> T.invoke(block: () -> Unit) = block()

    private fun test() {
        receiver { 42 }<<caret>Int>{}
    }
}
