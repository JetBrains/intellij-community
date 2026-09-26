// PROBLEM: none
// WITH_STDLIB
class CustomObject {
    val length: Int = 5
}

fun CustomObject.processItems(): List<Int> {
    return (0 until leng<caret>th).map { it }
}