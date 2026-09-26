fun interface BiProcessor {
    fun process(a: String, b: Int): Boolean
}

var x: BiProcessor = <caret>

// ELEMENT_TEXT: BiProcessor
// TAIL_TEXT: " { a, b -> ... } (function: (String, Int) -> Boolean) (<root>)"


