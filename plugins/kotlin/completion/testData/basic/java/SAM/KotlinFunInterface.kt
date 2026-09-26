fun interface MyInterface {
    fun run(): Unit
}

var a: MyInterface = <caret>

// EXIST: {"lookupString":"MyInterface","itemText":"MyInterface","tailText":" {...} (function: () -> Unit) (<root>)","typeText":"MyInterface", "icon":"Function"}
