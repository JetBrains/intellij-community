// PROBLEM: none
// WITH_RUNTIME
fun test(x : Any) {
    <caret>synchronized(x) {
        try {
            println(x)
        }
        catch (ex: Exception) {
            println(ex)
        }
    }
}