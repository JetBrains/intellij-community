// AFTER-WARNING: Parameter 'str' is never used, could be renamed to _
// AFTER-WARNING: Parameter 'x' is never used, could be renamed to _
// AFTER-WARNING: Variable 'num' is never used
class TestingUse {
    fun test4(printNum: (a: Int, b: String) -> Unit, c: Int): Int {
        printNum(c, "This number is")
        return c
    }
}

fun main() {
    val num = TestingUse().test4({ <caret>x, str -> }, 5)
}
