interface Maker<T> {
    fun make(): T
}

class Value(val text: String) {
    companion object Factory : Maker<Value> {
        val A = Value("a")

        override fun make(): Value = Value("b")
    }
}

fun test() {
    val value: Maker<Value> = <caret>
}

// EXIST: Value
// ABSENT: { "lookupString":"Factory", "itemText":"Value.Factory" }
