// PROBLEM: none
fun test(arr : Array<X>, i: Int, j: Int) {
    if (<caret>arr[i].a == arr[j].a) {}
}
class X {
    var a : Int = 0
}
