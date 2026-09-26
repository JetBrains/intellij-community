class Value(val number: Int) {
    companion object {
        val A = Value(0)
        val B = Value(1)
        fun make(number: Int): Value = Value(number)
    }
}

fun test() {
    val block: () -> Value = { <caret> }
}

// EXIST: { "lookupString":"A", "itemText":"Value.A" }
// EXIST: { "lookupString":"B", "itemText":"Value.B" }
// EXIST: { "lookupString":"make", "itemText":"Value.make" }