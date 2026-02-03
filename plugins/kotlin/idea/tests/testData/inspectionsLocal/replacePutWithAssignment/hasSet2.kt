// PROBLEM: none
// WITH_STDLIB
class Foo : HashMap<String, String>() {
    operator fun set(x: String, y: String) {
        println("wrong method")
    }
}

fun main() {
    Foo().<caret>put("x", "y")
}