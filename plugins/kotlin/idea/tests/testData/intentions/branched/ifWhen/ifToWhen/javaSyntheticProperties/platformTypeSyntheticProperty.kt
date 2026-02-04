// WITH_STDLIB
fun test(config: Config) {
    i<caret>f (config.value == "a") {
        println("a")
    } else if (config.value == "b") {
        println("b")
    } else if (config.value == "c") {
        println("c")
    }
}
