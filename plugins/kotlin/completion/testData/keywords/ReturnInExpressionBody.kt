// LANGUAGE_VERSION: 2.4

val s: String? = null
fun process(s: String): String = TODO()

fun f(): String = process(s ?: ret<caret>)

// EXIST: return
