class Value {
    companion object {
        val A = Value()
        fun make(): Value = Value()
        fun text(): String = ""
    }
}

fun test() {
    val value: Value? = <caret>
}

// EXIST: { "lookupString":"A", "itemText":"Value.A" }
// EXIST: { "lookupString":"make", "itemText":"Value.make" }
// ABSENT: { "lookupString":"text", "itemText":"Value.text" }
