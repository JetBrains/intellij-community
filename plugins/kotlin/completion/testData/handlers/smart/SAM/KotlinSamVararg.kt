fun interface Sam {
    fun combine(prefix: String, vararg values: String): String
}

var x: Sam = <caret>

// ELEMENT_TEXT: Sam
// TAIL_TEXT: " { prefix, values -> ... } (function: (String, Array<out String>) -> String) (<root>)"


