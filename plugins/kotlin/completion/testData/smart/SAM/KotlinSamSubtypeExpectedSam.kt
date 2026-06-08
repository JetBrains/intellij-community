fun interface BaseSam {
    fun run(a: Int, b: String)
}

fun interface SubSam : BaseSam

var x: BaseSam = <caret>

// EXIST: {"lookupString":"BaseSam","itemText":"BaseSam","tailText":" { a, b -> ... } (function: (Int, String) -> Unit) (<root>)","typeText":"BaseSam", "icon":"Function"}
// EXIST: {"lookupString":"SubSam","itemText":"SubSam","tailText":" { a, b -> ... } (function: (Int, String) -> Unit) (<root>)","typeText":"SubSam", "icon":"Function"}

