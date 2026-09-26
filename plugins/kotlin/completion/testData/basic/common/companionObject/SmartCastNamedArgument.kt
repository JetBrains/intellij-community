class Value private constructor(val text: String) {
    companion object {
        val A = Value("a")
        fun make(): Value = Value("c")
        fun from(text: String): Value = Value(text)
    }
}

open class Host {
    open fun take(value: Value) {}
    open fun more(value: Value) {}
}

fun test(item: Any) {
    when (item) {
        is Host -> item.more(value = <caret>)
    }
}

// EXIST: { "lookupString":"A", "itemText":"Value.A" }
// EXIST: { "lookupString":"make", "itemText":"Value.make" }
// EXIST: { "lookupString":"from", "itemText":"Value.from" }
