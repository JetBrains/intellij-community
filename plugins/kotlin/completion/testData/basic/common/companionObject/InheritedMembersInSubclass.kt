open class Base {
    companion object {
        val A = Base()
        fun makeBase(): Base = Base()
    }
}

class Child private constructor() : Base() {
    companion object {
        val B = Child()
        fun makeChild(): Child = Child()
    }

    val value: Base = <caret>
}

// EXIST: { "lookupString":"B", "itemText":"B" }
// EXIST: { "lookupString":"makeChild", "itemText":"makeChild" }
// EXIST: { "lookupString":"A", "itemText":"Base.A" }
// EXIST: { "lookupString":"makeBase", "itemText":"Base.makeBase" }
// ABSENT: { "lookupString":"B", "itemText":"Child.B" }
// ABSENT: { "lookupString":"makeChild", "itemText":"Child.makeChild" }
