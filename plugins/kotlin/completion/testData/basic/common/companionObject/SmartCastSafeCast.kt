class Value(val text: String) {
    companion object {
        val A = Value("a")
        val B = Value("b")
        fun make(): Value = Value("c")
        fun from(text: String): Value = Value(text)
    }
}

open class Host {
    open fun take(value: Value) {}
}

fun test(item: Any) {
    val host = item as? Host ?: return
    host.take(<caret>)
}

// EXIST: { "lookupString":"A", "itemText":"Value.A" }
// EXIST: { "lookupString":"B", "itemText":"Value.B" }
// EXIST: { "lookupString":"make", "itemText":"Value.make" }
// EXIST: { "lookupString":"from", "itemText":"Value.from" }