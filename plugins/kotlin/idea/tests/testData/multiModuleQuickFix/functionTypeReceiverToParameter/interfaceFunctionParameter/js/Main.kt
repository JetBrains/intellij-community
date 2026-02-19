// "Convert 'String.() -> Unit' to '(String) -> Unit'" "true"

external interface E {
    fun boo(p: Str<caret>ing.() -> Unit): String.() -> Unit
}

// ERROR: Function types with receiver are prohibited in external declarations