// "Change type to MutableList" "true"
// WITH_STDLIB
fun main() {
    val list = foo()
    list[1]<caret> = 10
}

fun foo() = listOf(1, 2, 3)