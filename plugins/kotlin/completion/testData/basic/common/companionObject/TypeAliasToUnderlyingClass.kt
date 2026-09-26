typealias Alias = Value

class Value private constructor() {
    companion object {
        val A = Value()
        fun make(): Value = Value()
    }
}

fun test() {
    val value: Alias = <caret>
}

// EXIST: { "lookupString":"A", "itemText":"Value.A" }
// EXIST: { "lookupString":"make", "itemText":"Value.make" }
