// FIR_COMPARISON
// WITH_MESSAGE: "Removed 1 import"
import foo.ArrayList
import foo.ArrayList

class Action {
    fun test() {
        val test : ArrayList<Int>? = null
    }
}