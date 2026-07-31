// PROBLEM: none
open class Test

fun Test.ext() = 100
class E : Test() {
    private <caret>inner class C {
        val x = ext()
    }
}