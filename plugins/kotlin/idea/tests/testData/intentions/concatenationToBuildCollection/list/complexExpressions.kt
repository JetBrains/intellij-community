fun main() {
    val list = listOf(1)
    val a = listOf(1, 2) +
       run {
           listOf(1)
       } +
            list() +<caret>
            set()+
            sequence() +
        5
}

private fun list(): List<Int> = emptyList()
private fun set(): Set<Int> = setOf()
private fun sequence(): Sequence<Int> = sequenceOf()