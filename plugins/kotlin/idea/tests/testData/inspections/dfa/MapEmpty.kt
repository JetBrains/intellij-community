// WITH_STDLIB
fun main() {
    // KTIJ-23058
    val myMap = hashMapOf<Int, Int>()

    myMap[1] = 2

    if (myMap.isNotEmpty()) {
    }
}