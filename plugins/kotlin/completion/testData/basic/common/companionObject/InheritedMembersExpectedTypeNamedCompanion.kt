open class Base<T> {
    open val base: T
        get() = error("override required")
    open fun make(): T = base
    open fun from(text: String): T = error("override required")
    val extra: T
        get() = base
    fun more(): T = make()
    fun next(text: String): T = from(text)
}

interface Mark<T> {
    fun mark(): T
}

class Value (val text: String) {
    companion object MyCompanion: Base<Value>(), Mark<Value> {
        override val base: Value = Value("a")
        val own = Value("o")
        override fun make(): Value = Value("m")
        override fun from(text: String): Value = Value(text)
        override fun mark(): Value = Value("k")
        fun other(): Value = Value("p")
    }
}

fun accept(param: Value) {
}

fun test() {
    accept(<caret>)
}

// EXIST: { "lookupString":"base", "itemText":"Value.MyCompanion.base" }
// EXIST: { "lookupString":"extra", "itemText":"Value.MyCompanion.extra" }
// EXIST: { "lookupString":"make", "itemText":"Value.MyCompanion.make" }
// EXIST: { "lookupString":"more", "itemText":"Value.MyCompanion.more" }
// EXIST: { "lookupString":"from", "itemText":"Value.MyCompanion.from" }
// EXIST: { "lookupString":"next", "itemText":"Value.MyCompanion.next" }
// EXIST: { "lookupString":"mark", "itemText":"Value.MyCompanion.mark" }
// EXIST: { "lookupString":"own", "itemText":"Value.MyCompanion.own" }
// EXIST: { "lookupString":"other", "itemText":"Value.MyCompanion.other" }
