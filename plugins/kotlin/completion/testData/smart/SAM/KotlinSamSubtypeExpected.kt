interface Base

fun interface Sam : Base {
    fun run()
}

var x: Base = <caret>

// EXIST: {"lookupString":"Sam","itemText":"Sam","tailText":" {...} (function: () -> Unit) (<root>)","typeText":"Sam", "icon":"Function"}

