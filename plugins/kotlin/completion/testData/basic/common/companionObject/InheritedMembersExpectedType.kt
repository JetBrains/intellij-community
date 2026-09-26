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
    companion object : Base<Value>(), Mark<Value> {
        override val base: Value = Value("a")
        val own = Value("o")
        override fun make(): Value = Value("m")
        override fun from(text: String): Value = Value(text)
        override fun mark(): Value = Value("k")
        fun other(): Value = Value("p")
    }
}

fun test() {
    val value: Value = <caret>
}

// EXIST: { "lookupString":"base", "itemText":"Value.base" }
// EXIST: { "lookupString":"extra", "itemText":"Value.extra" }
// EXIST: { "lookupString":"make", "itemText":"Value.make" }
// EXIST: { "lookupString":"more", "itemText":"Value.more" }
// EXIST: { "lookupString":"from", "itemText":"Value.from" }
// EXIST: { "lookupString":"next", "itemText":"Value.next" }
// EXIST: { "lookupString":"mark", "itemText":"Value.mark" }
// EXIST: { "lookupString":"own", "itemText":"Value.own" }
// EXIST: { "lookupString":"other", "itemText":"Value.other" }