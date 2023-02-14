// WITH_STDLIB
fun test() {
    <caret>when {
        false -> {
            println(1)
        }
    }
}