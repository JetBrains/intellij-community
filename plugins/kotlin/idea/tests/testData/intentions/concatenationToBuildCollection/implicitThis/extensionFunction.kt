fun MutableList<Int>.bbb() {
    fun List<String>.aaa() {
        listOf(get(0)) +<caret> add(1).toString()
    }
}