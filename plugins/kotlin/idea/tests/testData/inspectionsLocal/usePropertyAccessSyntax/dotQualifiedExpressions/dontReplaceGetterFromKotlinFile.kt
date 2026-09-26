// PROBLEM: none

fun main() {
    val k = K()
    k.<caret>getX()
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