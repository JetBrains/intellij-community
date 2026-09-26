// WITH_STDLIB
fun main() {
    val list = List(5) { it }
    mutableListOf<Int>().apply {
        for (element in list) {
            if (lastOrNull() != null) {
                this[lastIndex] += element
            } else {
                this += element
            }
        }
    }
}
