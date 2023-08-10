// PROBLEM: none
fun foo(k: K) {
    k.<caret>setX(0)
}

class K : J() {
    override fun getX(): Int {
        doSomething()
        return super.getX()
    }

    override fun setX(x: Int) {
        doSomething()
        super.setX(x)
    }

    private fun doSomething() {
    }
}