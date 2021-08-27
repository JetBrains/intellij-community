// PROBLEM: none
// WITH_RUNTIME
fun test() {
    <caret>X.x(try {X.get()} catch(e: Exception) {null}).trim()
}
class X {
    companion object {
        fun x(x: String?):String = x?:"abc"
        fun get(): String = "xyz"
    }
}