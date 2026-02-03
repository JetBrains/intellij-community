// "Convert 'String.() -> Unit' to '(String) -> Unit'" "true"

external fun boo(p: String.() -> Unit): Str<caret>ing.() -> Unit

// ERROR: Function types with receiver are prohibited in external declarations