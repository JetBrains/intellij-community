// WITH_STDLIB

fun main() {
    // KTIJ-24286
    val s = java.lang.<warning descr="[PLATFORM_CLASS_MAPPED_TO_KOTLIN]">String</warning>("string") as String
    println(s)
}