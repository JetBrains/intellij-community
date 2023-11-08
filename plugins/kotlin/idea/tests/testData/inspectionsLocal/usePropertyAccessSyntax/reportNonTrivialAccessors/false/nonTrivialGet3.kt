// HIGHLIGHT: INFORMATION
fun foo(k: K) {
    k.<caret>getX()
}

class K : J() {
    override fun getX(): Int {
        doSomething()
        return super.getX()
    }

    private fun doSomething() {
    }
}