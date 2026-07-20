fun main() {
    val a = setOf(1,2) +<caret> listOf(1).mapTo(mutableListOf()) { it }
}

