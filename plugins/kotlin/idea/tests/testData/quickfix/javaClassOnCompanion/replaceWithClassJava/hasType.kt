// "Replace with '::class.java'" "true"
// WITH_STDLIB
// DISABLE-ERRORS
fun main() {
    val c: Class<Int.Companion> = Int.javaClass<caret>
}