// "Replace with '::class.java'" "true"
// WITH_STDLIB
fun main() {
    val name = Int.javaClass<caret>.name
}