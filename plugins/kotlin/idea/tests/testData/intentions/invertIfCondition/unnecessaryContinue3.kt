// WITH_STDLIB
fun test() {
    for (x in "abc") {
        if (x != 'a') {
            <caret>if (x == 'b') {
                println("foo")
                continue
            }
            println("else")
        }
    }
}