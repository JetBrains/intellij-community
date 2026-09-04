// WITH_STDLIB
// HIGHLIGHT: INFORMATION

data class Name(val value: String)

fun test(names: List<Name>) {
    for (<caret>name in names) {
        println(name.value)
    }
}