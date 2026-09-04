// PROBLEM: none
class Foo {
    init {
        <caret>onInit()
    }

    fun onInit() {
        println("initialized")
    }
}
