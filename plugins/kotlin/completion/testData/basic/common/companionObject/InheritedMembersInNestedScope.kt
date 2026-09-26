open class Base {
    companion object {
        val A = Base()
        fun make(): Base = Base()
    }
}

class Child : Base() {
    object Scope {
        val value: Base = <caret>
    }
}

// EXIST: { "lookupString":"A", "itemText":"Base.A" }
// EXIST: { "lookupString":"make", "itemText":"Base.make" }
