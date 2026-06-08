fun interface MyInterface {
    fun run(): Unit
}

var a: MyInterface = <caret>

// ELEMENT_TEXT: MyInterface
// TAIL_TEXT: " {...} (function: () -> Unit) (<root>)"


