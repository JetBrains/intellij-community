fun main() {
    val a = setOf(1, 2) +<caret>
    listOf(3, 4) +
            5 +
            sequenceOf(6, 7) +
            setOf(1,2) +
            8 +
            hashSetOf(1) +
            mutableListOf(1) +
            mutableSetOf(11)
}