// WITH_STDLIB
fun test(container: Container) {
    i<caret>f (container.isEmpty) {
        println("empty")
    } else if (!container.isEmpty) {
        println("not empty")
    }
}
