// PROBLEM: none
// ERROR: Unresolved reference: got
// K2_ERROR: UNRESOLVED_REFERENCE
fun test() {
    class Test{
        operator fun get(i: Int) : Int = 0
    }
    val test = Test()
    test.g<caret>ot(0)
}
