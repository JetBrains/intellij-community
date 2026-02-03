// PROBLEM: none
// WITH_STDLIB

fun main() {
    val k = K()
    with(k) {
        <caret>getX()
    }
}

class K {
    private var x: Int = 0

    fun getX(): Int {
        return x
    }

    fun setX(x: Int) {
        this.x = x
    }
}