class Value private constructor() {
    companion object {
        private val A = Value()
        val B = Value()
        private fun make() = Value()
        fun mark(): Value {
            return Value()
        }
    }
}

fun test() {
    val value: Value = <caret>
}

// ABSENT: { "lookupString":"A", "itemText":"Value.A" }
// ABSENT: { "lookupString":"make", "itemText":"Value.make" }
// EXIST: { "lookupString":"B", "itemText":"Value.B" }
// EXIST: { "lookupString":"mark", "itemText":"Value.mark" }