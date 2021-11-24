// PROBLEM: none
// WITH_STDLIB
fun test() {
    val c = MyClass()
    <caret>with(c) {
        println(f())
    }
}

class MyClass {
    fun f(): String = ""
}