// "Convert to 'forEach'" "true"
// WITH_STDLIB
fun List<String>.test() {
    <caret>forEachIndexed { <caret>index, s ->
        println(s)
    }
}