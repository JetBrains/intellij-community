// "Import" "false"
// ACTION: Create extension function 'Some.get'
// ACTION: Create member function 'Some.get'
// ERROR: Unresolved reference: some[12]
// ERROR: No get method providing array access
// K2_AFTER_ERROR: No 'get' operator method providing array access.

package Teting

class Some() {
//    fun get(i : Int) : Int {
//        return i
//    }
}

fun main(args : Array<String>) {
    val some = Some()
    // Nothing should be changed
    <caret>some[12]
}