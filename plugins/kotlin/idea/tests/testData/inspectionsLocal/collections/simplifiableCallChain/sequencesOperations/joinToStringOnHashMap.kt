// PROBLEM: none
// WITH_STDLIB

fun test(data: HashMap<String, String>) {
    val result = data.<caret>map { "${it.key}: ${it.value}" }.joinToString("\n")
}