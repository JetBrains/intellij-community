// "Replace with 'Companion::class.java'" "true"
// WITH_STDLIB
fun main() {
    val c: Class<Int.Companion> = Int.javaClass<caret>
}