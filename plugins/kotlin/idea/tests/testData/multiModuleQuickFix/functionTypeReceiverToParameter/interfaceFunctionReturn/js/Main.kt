// "Convert 'Int.() -> Unit' to '(Int) -> Unit'" "true"
// IGNORE_K2
external interface E {
    fun boo(p: String.() -> Unit): In<caret>t.() -> Unit
}

// ERROR: Function types with receiver are prohibited in external declarations