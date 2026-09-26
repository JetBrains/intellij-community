// FIX: Use property access syntax

fun main() {
    val k = K()
    k.<caret>getX()
}

class K: J() {
    override fun getX(): Int {
        return super.getX()
    }

    override fun setX(x: Int) {
        super.setX(x)
    }
}