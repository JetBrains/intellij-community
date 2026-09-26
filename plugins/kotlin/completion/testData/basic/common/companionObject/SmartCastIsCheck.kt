class Value private constructor(val text: String) {
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

fun test(value: Any) {
    if (value is Host) {
        value.take(<caret>)
    }
}

// EXIST: { "lookupString":"A", "itemText":"Value.A" }
// EXIST: { "lookupString":"B", "itemText":"Value.B" }
// EXIST: { "lookupString":"make", "itemText":"Value.make" }
// EXIST: { "lookupString":"from", "itemText":"Value.from" }