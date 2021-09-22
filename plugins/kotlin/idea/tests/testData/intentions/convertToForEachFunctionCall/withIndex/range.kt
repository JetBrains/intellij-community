// WITH_RUNTIME
fun test() {
    <caret>for ((index, element) in (1..3).withIndex()) {
        println("$index:$element")
    }
}