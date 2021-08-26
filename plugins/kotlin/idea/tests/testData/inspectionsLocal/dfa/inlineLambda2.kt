// PROBLEM: none
// WITH_RUNTIME
fun foo3FpLambda(ints: Array<Int>, b : Boolean) {
    var x = 0
    ints.forEach {
        x = 1
        if (b) return@forEach
        print(it)
    }
    if (<caret>b && x == 1)
        print("abacaba")
}