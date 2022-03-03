// WITH_STDLIB

fun println(s: String) {}

fun foo() {
    val a = <caret>when ("") {
        else -> {
            println("")
            1
        }
    }
}