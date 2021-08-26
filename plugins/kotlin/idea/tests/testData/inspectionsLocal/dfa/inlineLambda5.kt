// PROBLEM: none
// WITH_RUNTIME
fun test(x : Int): Int {
    return <caret>x.let { y ->
        if (y > 0) return@let 10
        if (y < 0) return 15
        return@let 20
    }
}