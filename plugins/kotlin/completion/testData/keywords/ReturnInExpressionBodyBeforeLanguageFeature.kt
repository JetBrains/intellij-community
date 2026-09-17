// LANGUAGE_VERSION: 2.2

val s: String? = null
fun process(s: String): String = TODO()

fun f(): String = process(s ?: ret<caret>)

// ABSENT: return
