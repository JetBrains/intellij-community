// PROBLEM: none
// WITH_STDLIB

fun Int.foo(): Int {
    println("side effect")
    return this
}

fun main() {
    42.foo().let<caret> {
        println("")
    }
}
