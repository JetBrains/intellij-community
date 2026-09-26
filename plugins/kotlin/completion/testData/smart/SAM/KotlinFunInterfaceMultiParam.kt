fun interface BiProcessor {
    fun process(a: String, b: Int): Boolean
}

var x: BiProcessor = <caret>

// EXIST: {"lookupString":"BiProcessor","itemText":"BiProcessor","tailText":" { a, b -> ... } (function: (String, Int) -> Boolean) (<root>)","typeText":"BiProcessor"}
