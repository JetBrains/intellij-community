// PROBLEM: none
// WITH_STDLIB

class A {
    val list: MutableList<String> = mutableListOf()
}

class B {
    val list: MutableList<String> = mutableListOf()
}

fun Any.add(s: String) {
    <caret>when (this) {
        is A -> list += s
        is B -> list += s
        else -> throw IllegalStateException()
    }
}