// WITH_STDLIB

fun main() {
    // KTIJ-24286
    val s = java.lang.String("string") as String
    println(s)
}