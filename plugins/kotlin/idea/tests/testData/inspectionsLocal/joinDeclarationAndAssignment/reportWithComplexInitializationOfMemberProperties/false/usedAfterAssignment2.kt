// PROBLEM: none
class A {
    <caret>val input: String

    init {
        input = ""
        println(input)
    }
}

fun println(s: String) {}