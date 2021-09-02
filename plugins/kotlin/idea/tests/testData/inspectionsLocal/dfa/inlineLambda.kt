// PROBLEM: Condition is always false
// FIX: none
// WITH_RUNTIME
fun foo3FpLambda(ints: Array<Int>, b : Boolean) {
    var x = 0
    ints.forEach {
        if (b) return
        x++
        print(it)
    }
    if (<caret>b && x == 1)
        print("abacaba")
}