fun interface Transformer<T, R> {
    fun transform(input: T): R
}

var t: Transformer<String, Int> = <caret>

// EXIST: {"lookupString":"Transformer","itemText":"Transformer","tailText":" {...} (function: (T) -> R) (<root>)","typeText":"Transformer<T, R>"}
