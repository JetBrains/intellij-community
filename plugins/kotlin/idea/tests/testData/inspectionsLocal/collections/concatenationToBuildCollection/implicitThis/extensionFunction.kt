// FIX: Convert to collection builder

fun MutableList<Int>.bbb() {
    fun List<String>.aaa() {
        listOf(get(0)) +<caret> add(1).toString()
    }
}