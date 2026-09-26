data class Data(override val text: String) : Type

interface Type {
    val text: String

    companion object {
        val X: Type = Data("x")
        fun make(): Type = Data("y")
    }
}

fun test() {
    val value: Type = <caret>
}

// EXIST: { "lookupString":"X", "itemText":"Type.X" }
// EXIST: { "lookupString":"make", "itemText":"Type.make" }
