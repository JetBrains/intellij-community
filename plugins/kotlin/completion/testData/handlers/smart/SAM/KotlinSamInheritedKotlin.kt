interface Base {
    fun run()
}

fun interface Sam : Base

var x: Sam = <caret>

// ELEMENT_TEXT: Sam
// TAIL_TEXT: " {...} (function: () -> Unit) (<root>)"


