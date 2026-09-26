import java.util.function.Consumer

var a: Consumer<String> = <caret>

// EXIST: {"lookupString":"Consumer","itemText":"Consumer","tailText":" {...} (function: (T) -> Unit) (java.util.function)","typeText":"Consumer<T>"}
