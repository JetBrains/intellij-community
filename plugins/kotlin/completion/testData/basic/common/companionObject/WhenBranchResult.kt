class Value(val number: Int) {
    companion object {
        val A = Value(0)
        fun make(number: Int): Value = Value(number)
    }
}

fun test(number: Int): Value = when (number) {
    0 -> <caret>
    else -> Value.B
}

// EXIST: { "lookupString":"A", "itemText":"Value.A" }
// EXIST: { "lookupString":"make", "itemText":"Value.make" }
