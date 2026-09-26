class Value(val text: String) {
    companion object {
        val A = Value("a")
        fun from(text: String): Value = Value(text)
    }
}

open class Host {
    open fun take(value: Value) {}
}

fun test(host: Host?) {
    if (host != null) {
        host.take(<caret>)
    }
}

// EXIST: { "lookupString":"A", "itemText":"Value.A" }
// EXIST: { "lookupString":"from", "itemText":"Value.from" }