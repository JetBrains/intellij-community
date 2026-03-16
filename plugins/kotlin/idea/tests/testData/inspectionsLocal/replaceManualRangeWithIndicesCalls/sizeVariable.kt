// PROBLEM: none
// WITH_STDLIB
class CustomObject {
    val size: Int = 5
}

fun CustomObject.processItems(): List<Int> {
    return (0 until siz<caret>e).map { it }
}