// PROBLEM: none

fun main() {
    val j = J()
    j.<caret>getX()
}

open class K {
    private var x: Int = 0

    open fun getX(): Int {
        return x
    }

    open fun setX(x: Int) {
        this.x = x
    }
}