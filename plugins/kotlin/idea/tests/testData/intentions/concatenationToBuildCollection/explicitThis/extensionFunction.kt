fun MutableList<Int>.bbb() {
    fun List<String>.aaa() {
        this + this@bbb.map { it.toString() } + "a" + this.size.toString() +<caret>  this[0]
    }
}