// WITH_STDLIB

fun foo() {
    val a = <caret>when ("") {
        else -> kotlin.run { 1 }
    }
}
