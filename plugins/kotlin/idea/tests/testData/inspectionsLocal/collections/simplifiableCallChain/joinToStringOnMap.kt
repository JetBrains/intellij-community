// PROBLEM: none
// WITH_STDLIB

val data = mutableMapOf<String, String>()
val result = data.<caret>map { "${it.key}: ${it.value}" }.joinToString("\n")