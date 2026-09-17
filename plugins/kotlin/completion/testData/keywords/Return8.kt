// LANGUAGE_VERSION: 2.4
fun foo(p: Int?): Int = p ?: <caret>

// EXIST: return
