// REPORT_NON_TRIVIAL_ACCESSORS: true
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