// PROBLEM: Condition is always false
// FIX: none
fun test() {
    var x : Any = object : X(), Y {}
    if (<caret>x is Int) {}
}
open class X {}
interface Y {

}