fun interface `Foo-Bar` {
    fun run()
}

fun test() {
    val action = <caret>`Foo-Bar` {}
}
