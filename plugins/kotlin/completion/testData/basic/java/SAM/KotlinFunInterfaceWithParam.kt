fun interface Processor {
    fun process(input: String): Int
}

var a: Processor = <caret>

// EXIST: {"lookupString":"Processor","itemText":"Processor","tailText":" {...} (function: (String) -> Int) (<root>)","typeText":"Processor"}
