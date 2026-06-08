fun interface Sam {
    fun run()
}

var x: Sam? = <caret>

// ELEMENT_TEXT: Sam
// TAIL_TEXT: " {...} (function: () -> Unit) (<root>)"


