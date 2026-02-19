val MutableList<Int>.bbb: Int
    get() {
        fun List<String>.aaa() {
            listOf(get(0)) +<caret> add(1).toString()
        }
        return 1
    }