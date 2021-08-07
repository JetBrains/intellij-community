// PROBLEM: none
data class Point(var x: Int) {
    fun test() {
        val xx = x
        update()
        if (<caret>xx == x) {}
    }

    fun update() {
        x++
    }
}