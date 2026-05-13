// WITH_STDLIB
// IGNORE_K1
fun test(map: Map<String, Int>) {
    for (<caret>entry in map.entries) {
        if (entry.key == "my_name") println("My index is ${entry.value}")
    }
}