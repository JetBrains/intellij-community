// "Convert 'String.() -> Unit' to '(String) -> Unit'" "true"
external fun boo(p: Str<caret>ing.() -> Unit): String.() -> Unit

// ERROR: Function types with receiver are prohibited in external declarations