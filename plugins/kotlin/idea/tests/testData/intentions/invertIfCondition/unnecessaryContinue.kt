// WITH_STDLIB
fun test() {
    for (x in "abc") {
        <caret>if (x == 'a') continue
        println("else")
    }
}