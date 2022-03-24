package test

class Test {
    <caret>inner class Inner

    fun foo() {
        this.Inner()
    }
}