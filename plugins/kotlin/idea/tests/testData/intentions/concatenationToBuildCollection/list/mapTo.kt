fun main() {
    val a = listOf(1,2) +<caret> listOf(1).mapTo(mutableListOf()) { it }
}

