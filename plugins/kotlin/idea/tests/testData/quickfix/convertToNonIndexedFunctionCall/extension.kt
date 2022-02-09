// "Convert to 'forEach'" "true"
// WITH_STDLIB
fun List<String>.test() {
    forEachIndexed { <caret>index, s ->
        println(s)
    }
}