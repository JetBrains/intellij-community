fun interface Greeter {
    fun greet(name: String): String

    fun greetAll(names: List<String>): String = names.joinToString { greet(it) }
}

var g: Greeter = <caret>

// EXIST: {"lookupString":"Greeter","itemText":"Greeter","tailText":" {...} (function: (String) -> String) (<root>)","typeText":"Greeter"}
