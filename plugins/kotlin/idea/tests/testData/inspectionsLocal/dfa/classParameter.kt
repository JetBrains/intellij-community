// PROBLEM: none
class X(private val x:Boolean) {
    fun test(other : X) {
        if (x) {

        } else if (!other.<caret>x) {

        }
    }
}
